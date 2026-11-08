package com.onefilm.android.tv.player.danmaku;

import android.util.Log;

import com.github.catvod.net.OkHttp;
import com.onefilm.android.tv.App;
import com.onefilm.android.tv.api.DanmakuApi;
import com.onefilm.android.tv.bean.Danmaku;
import com.onefilm.android.tv.core.danmaku.DanmakuQuery;
import com.onefilm.android.tv.setting.DanmakuSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import androidx.media3.ui.danmaku.DanmakuParser;
import okhttp3.Response;

/**
 * 弹幕解析：与上游 FongMi 一致的单次查询 + 按年份/片名选源。
 *
 * 服务器返回全部同名候选（可能包含错误版本），这里按“年份匹配 > 片名完全匹配 >
 * 片名包含”排序，只对前 3 条做 XML 校验，选中第一条真正有弹幕的源。
 */
public final class DanmakuResolver {

    private static final String TAG = "DanmakuResolver";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final long SEARCH_TIMEOUT_MS = 60_000L;
    private static final long LOAD_TIMEOUT_MS = 45_000L;
    private static final long RETRY_DELAY_MS = 500L;
    /** 同一部剧的源列表缓存 10 分钟；换集只重选源，不重复搜索。 */
    private static final long SOURCE_CACHE_TTL_MS = 10 * 60 * 1000L;
    private static volatile String cachedShowKey = "";
    private static volatile List<Danmaku> cachedSources = List.of();
    private static volatile long cachedAt = 0L;
    /** 每个平台最多 1 条，最多 6 条：bilibili > 360 > imgo > youku > iqiyi > tencent > migu。 */
    private static final int MAX_SOURCES = 6;

    private DanmakuResolver() {
    }

    public static void cancel() {
        GENERATION.incrementAndGet();
        DanmakuApi.cancel();
    }

    /** 接口地址变更后清空源列表缓存，避免继续复用旧服务端的搜索结果。 */
    public static void resetCache() {
        cachedShowKey = "";
        cachedSources = List.of();
        cachedAt = 0L;
    }

    /**
     * @param name           影片名（可含年份/语言/演员版本）
     * @param episode        集名（可含数字）
     * @param year           影片年份（详情页/源站提供，可为空）
     * @param spiderDanmakus 源站自带弹幕
     * @param set            直接替换当前弹幕源
     * @param add            追加弹幕源（源站优先模式）
     */
    public static void resolve(String name, String episode, String year,
                               List<Danmaku> spiderDanmakus,
                               Consumer<Danmaku> set, Consumer<Danmaku> add) {
        final int generation = GENERATION.get();
        final String baseName = DanmakuQuery.INSTANCE.bestName(name);
        final String episodeQuery = DanmakuQuery.INSTANCE.bestEpisode(episode, 1);
        String detectedYear = year == null ? "" : year.trim();
        if (detectedYear.isEmpty()) {
            String fromName = DanmakuQuery.INSTANCE.yearOf(name);
            detectedYear = fromName == null ? "" : fromName;
        }
        final String targetYear = detectedYear;
        final String showKey = baseName + "|" + targetYear;

        EXECUTOR.execute(() -> {
            if (generation != GENERATION.get()) return;
            List<Danmaku> items;
            if (canReuseSources(showKey, episodeQuery)) {
                items = cachedSources;
                Log.d(TAG, "reuse sources=" + items.size() + " show=" + showKey
                        + " episode=" + episodeQuery);
            } else {
                items = searchBlocking(baseName, episodeQuery);
                Log.d(TAG, "search name=" + baseName + " episode=" + episodeQuery
                        + " year=" + targetYear + " sources=" + items.size());
                if (!items.isEmpty()) {
                    cachedShowKey = showKey;
                    cachedSources = items;
                    cachedAt = System.currentTimeMillis();
                }
            }
            if (items.isEmpty()) return;

            List<Danmaku> ranked = new ArrayList<>(items);
            ranked.sort((a, b) -> Integer.compare(score(b, targetYear, baseName, episodeQuery), score(a, targetYear, baseName, episodeQuery)));

            // 只保留“最匹配那一档”的源，避免为了凑平台而拿到错误版本；
            // 每个平台最多 1 条，再按平台优先级排序，最多 6 条。
            int bestBase = baseScore(ranked.get(0), targetYear, baseName, episodeQuery);
            Map<String, Danmaku> byPlatform = new LinkedHashMap<>();
            for (Danmaku item : ranked) {
                if (baseScore(item, targetYear, baseName, episodeQuery) < bestBase - 400) break;
                String sourceName = item.getName() == null ? "" : item.getName();
                String platform = DanmakuQuery.INSTANCE.platformKey(sourceName);
                if (platform.isEmpty()) platform = sourceName;
                if (!byPlatform.containsKey(platform)) byPlatform.put(platform, item);
            }
            List<Danmaku> ordered = new ArrayList<>(byPlatform.values());
            ordered.sort(Comparator.comparingInt(item -> DanmakuQuery.INSTANCE.platformRank(item.getName() == null ? "" : item.getName())));
            Log.d(TAG, "sources=" + items.size() + " picked=" + ordered.size() + " platforms=" + byPlatform.keySet());

            int tried = 0;
            for (Danmaku item : ordered) {
                if (generation != GENERATION.get()) return;
                if (tried++ >= MAX_SOURCES) break;
                Log.d(TAG, "source score=" + score(item, targetYear, baseName, episodeQuery)
                        + " platform=" + DanmakuQuery.INSTANCE.platformOf(item.getName() == null ? "" : item.getName())
                        + " name=" + item.getName());
                String xml = download(item.getUrl());
                if (xml == null) {
                    // 第一次超时/失败时，服务端通常已把结果写进缓存；同一 URL 再要一次只需几秒。
                    Log.d(TAG, "retry same url=" + item.getUrl());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    xml = download(item.getUrl());
                }
                int count = xml == null ? -1 : DanmakuParser.parseXml(xml).size();
                Log.d(TAG, "candidate len=" + (xml == null ? -1 : xml.length())
                        + " items=" + count + " url=" + item.getUrl());
                if (xml == null || count == 0) continue;
                final Danmaku chosen = item;
                App.post(() -> {
                    if (generation != GENERATION.get()) return;
                    boolean spiderFirst = DanmakuSetting.isSpiderFirst()
                            && spiderDanmakus != null
                            && !spiderDanmakus.isEmpty();
                    if (spiderFirst) add.accept(chosen);
                    else set.accept(chosen);
                });
                return;
            }
        });
    }

    /**
     * 换集复用：同一部剧（片名+年份）且缓存未过期，并且缓存里确实有当前集。
     * 电影没有“第N集”标记时视为可复用。
     */
    private static boolean canReuseSources(String showKey, String episode) {
        List<Danmaku> sources = cachedSources;
        if (!showKey.equals(cachedShowKey) || sources.isEmpty()) return false;
        if (System.currentTimeMillis() - cachedAt >= SOURCE_CACHE_TTL_MS) return false;
        boolean marked = false;
        for (Danmaku item : sources) {
            Integer number = DanmakuQuery.INSTANCE.episodeOf(item.getName() == null ? "" : item.getName());
            if (number == null) continue;
            marked = true;
            if (String.valueOf(number).equals(episode)) return true;
        }
        return !marked;
    }

    private static int score(Danmaku item, String year, String baseName, String episode) {
        String sourceName = item.getName() == null ? "" : item.getName();
        return DanmakuQuery.INSTANCE.scoreSource(sourceName, year, baseName, episode);
    }

    private static int baseScore(Danmaku item, String year, String baseName, String episode) {
        String sourceName = item.getName() == null ? "" : item.getName();
        return DanmakuQuery.INSTANCE.baseScore(sourceName, year, baseName, episode);
    }

    private static String download(String url) {
        try {
            Response response = OkHttp.newCall(OkHttp.client(LOAD_TIMEOUT_MS), url).execute();
            try (Response closeable = response) {
                if (!closeable.isSuccessful() || closeable.body() == null) return null;
                return closeable.body().string();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<Danmaku> searchBlocking(String name, String episode) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Danmaku>> result = new AtomicReference<>(List.of());
        try {
            DanmakuApi.search(
                    name,
                    episode,
                    items -> {
                        result.set(items == null ? List.of() : items);
                        latch.countDown();
                    },
                    error -> latch.countDown()
            );
        } catch (Throwable t) {
            return List.of();
        }
        try {
            latch.await(SEARCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }
}
