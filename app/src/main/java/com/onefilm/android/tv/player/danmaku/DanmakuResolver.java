package com.onefilm.android.tv.player.danmaku;

import android.util.Log;

import com.github.catvod.net.OkHttp;
import com.onefilm.android.tv.App;
import com.onefilm.android.tv.api.DanmakuApi;
import com.onefilm.android.tv.bean.Danmaku;
import com.onefilm.android.tv.core.danmaku.DanmakuQuery;
import com.onefilm.android.tv.setting.DanmakuSetting;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import androidx.media3.ui.danmaku.DanmakuParser;
import kotlin.Pair;

/**
 * 弹幕解析：标题/集名候选 + 多候选校验，供旧版播放页（手机/旧 TV）使用。
 *
 * 与 Compose 播放器共用 [DanmakuQuery] 的候选规则；逐条下载 XML，
 * 只把真正有弹幕的候选交给播放器，避免“第一条是空弹幕”导致整片无弹幕。
 */
public final class DanmakuResolver {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String TAG = "DanmakuResolver";
    private static final long TOTAL_BUDGET_MS = 25_000L;
    private static final long SEARCH_TIMEOUT_MS = 12_000L;
    private static final int MAX_CANDIDATES = 8;

    private DanmakuResolver() {
    }

    /**
     * @param spiderDanmakus 源站自带的弹幕（可能为空）
     * @param set            直接替换当前弹幕源
     * @param add            追加弹幕源（源站优先模式）
     */
    public static void resolve(String name, String episode, List<Danmaku> spiderDanmakus,
                               Consumer<Danmaku> set, Consumer<Danmaku> add) {
        EXECUTOR.execute(() -> {
            long deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS;
            for (Pair<String, String> combo : DanmakuQuery.INSTANCE.combinations(name, episode, 6)) {
                if (System.currentTimeMillis() >= deadline) return;
                List<Danmaku> items = searchBlocking(combo.getFirst(), combo.getSecond());
                Log.d(TAG, "search name=" + combo.getFirst() + " episode=" + combo.getSecond() + " sources=" + items.size());
                int tried = 0;
                for (Danmaku item : items) {
                    if (tried++ >= MAX_CANDIDATES || System.currentTimeMillis() >= deadline) break;
                    try {
                        String xml = OkHttp.string(item.getUrl());
                        int count = xml == null ? -1 : DanmakuParser.parseXml(xml).size();
                        Log.d(TAG, "candidate len=" + (xml == null ? -1 : xml.length()) + " items=" + count + " url=" + item.getUrl());
                        if (xml == null || count == 0) continue;
                        boolean spiderFirst = DanmakuSetting.isSpiderFirst()
                                && spiderDanmakus != null
                                && !spiderDanmakus.isEmpty();
                        App.post(() -> {
                            if (spiderFirst) add.accept(item);
                            else set.accept(item);
                        });
                        return;
                    } catch (Throwable ignored) {
                        // 该候选不可用，继续下一条
                    }
                }
            }
        });
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
