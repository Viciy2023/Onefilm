package com.onefilm.android.tv.player.adblock;

import android.os.SystemClock;
import android.util.Log;

import com.onefilm.android.tv.core.adblock.M3u8AdFilter;
import com.onefilm.android.tv.core.adblock.TsPtsReader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * PTS 探测：对每个 discontinuity 组的首分片读取 2KB，解析 MPEG-TS 里的首个视频 PTS。
 *
 * <p>成本控制：
 * <ul>
 *   <li>单请求 500ms 超时、总预算 800ms；</li>
 *   <li>请求并发执行，最多同时 8 个；</li>
 *   <li>结果按分片 URL 缓存 10 分钟，命中缓存不发请求；</li>
 *   <li>任何失败都返回 null，由上层视为“无证据”，绝不删除。</li>
 * </ul>
 *
 * <p>本方法运行在 media3 的 loader 线程，允许有限阻塞。
 */
public final class HlsPtsProbe implements M3u8AdFilter.PtsProbe {

    private static final String TAG = "HlsPtsProbe";

    private static final int RANGE_BYTES = 2048;
    private static final int READ_BYTES = 4096;
    private static final long REQUEST_TIMEOUT_MS = 800L;
    private static final long TOTAL_BUDGET_MS = 800L;
    private static final int MAX_CONCURRENCY = 8;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final int CACHE_MAX_ENTRIES = 512;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Mobile Safari/537.36";

    private static final OkHttpClient CLIENT = buildClient();

    private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private static OkHttpClient buildClient() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(MAX_CONCURRENCY);
        dispatcher.setMaxRequestsPerHost(MAX_CONCURRENCY);
        return new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    private static final class CacheEntry {
        final Long ptsMillis;
        final long storedAt;

        CacheEntry(Long ptsMillis, long storedAt) {
            this.ptsMillis = ptsMillis;
            this.storedAt = storedAt;
        }
    }

    @Override
    public List<Long> probe(List<String> groupFirstSegmentUrls) {
        long startedAt = SystemClock.elapsedRealtime();
        int size = groupFirstSegmentUrls == null ? 0 : groupFirstSegmentUrls.size();
        Long[] result = new Long[size];
        if (size == 0) return Arrays.asList(result);

        List<Integer> misses = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < size; i++) {
            String url = groupFirstSegmentUrls.get(i);
            CacheEntry entry = url == null ? null : CACHE.get(url);
            if (entry != null && now - entry.storedAt <= CACHE_TTL_MS) {
                result[i] = entry.ptsMillis;
            } else {
                misses.add(i);
            }
        }
        if (misses.isEmpty()) return Arrays.asList(result);

        long deadline = SystemClock.elapsedRealtime() + TOTAL_BUDGET_MS;
        CountDownLatch latch = new CountDownLatch(misses.size());
        for (int index : misses) {
            String url = groupFirstSegmentUrls.get(index);
            fetch(url, deadline, pts -> {
                result[index] = pts;
                latch.countDown();
            });
        }

        try {
            latch.await(TOTAL_BUDGET_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int hits = 0;
        for (Long value : result) {
            if (value != null) hits++;
        }
        Log.d(TAG, "probe groups=" + size
                + " hits=" + hits
                + " cost=" + (SystemClock.elapsedRealtime() - startedAt) + "ms");
        return Arrays.asList(result);
    }

    private void fetch(String url, long deadline, Consumer<Long> onDone) {
        long remaining = deadline - SystemClock.elapsedRealtime();
        if (url == null || remaining <= 0) {
            onDone.accept(null);
            return;
        }

        Request request;
        try {
            request = new Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-" + (RANGE_BYTES - 1))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .build();
        } catch (Throwable t) {
            onDone.accept(null);
            return;
        }

        Call call = CLIENT.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                onDone.accept(null);
            }

            @Override
            public void onResponse(Call call, Response response) {
                Long pts = null;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        byte[] bytes = readAtMost(body, READ_BYTES);
                        pts = TsPtsReader.INSTANCE.readPtsMillis(bytes);
                    }
                } catch (Throwable ignored) {
                    // 探测失败不产生任何副作用
                }
                if (pts != null) {
                    putCache(url, pts);
                }
                onDone.accept(pts);
            }
        });
    }

    private static byte[] readAtMost(ResponseBody body, int limit) throws java.io.IOException {
        try (InputStream input = body.byteStream()) {
            byte[] buffer = new byte[limit];
            int total = 0;
            while (total < limit) {
                int read = input.read(buffer, total, limit - total);
                if (read < 0) break;
                total += read;
            }
            return total == limit ? buffer : Arrays.copyOf(buffer, total);
        }
    }

    private static void putCache(String url, Long pts) {
        if (CACHE.size() > CACHE_MAX_ENTRIES) {
            long now = System.currentTimeMillis();
            CACHE.entrySet().removeIf(e -> now - e.getValue().storedAt > CACHE_TTL_MS);
            if (CACHE.size() > CACHE_MAX_ENTRIES) CACHE.clear();
        }
        CACHE.put(url, new CacheEntry(pts, System.currentTimeMillis()));
    }
}
