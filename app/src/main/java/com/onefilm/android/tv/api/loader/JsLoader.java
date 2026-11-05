package com.onefilm.android.tv.api.loader;

import com.onefilm.android.tv.App;
import com.fongmi.quickjs.crawler.Loader;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsLoader {

    /** 回收时保留最近这段时间内被使用过的引擎，避免掐断正在进行的详情/播放调用。 */
    private static final long RETAIN_GRACE_MS = 20_000L;

    /** 单站点释放的保护窗口：刚被取用过的引擎先不销毁（可能在跑详情/播放）。 */
    private static final long RELEASE_GRACE_MS = 500L;

    private static final java.util.concurrent.atomic.AtomicInteger INIT_FAILURES = new java.util.concurrent.atomic.AtomicInteger();

    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Long> lastAccess;
    private final Loader loader;
    private volatile String recent;

    public JsLoader() {
        spiders = new ConcurrentHashMap<>();
        lastAccess = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        Module.get().clear();
        spiders.clear();
        lastAccess.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        lastAccess.put(key, System.currentTimeMillis());
        return spiders.computeIfAbsent(key, k -> {
            Spider spider = null;
            try {
                spider = loader.spider(api, BaseLoader.get().dex(jar));
                spider.siteKey = key;
                spider.init(App.get(), ext);
                com.onefilm.android.tv.utils.SearchDebug.log("init-ok site=" + key);
                return spider;
            } catch (Throwable e) {
                // init 失败时引擎可能已经建好（QuickJS 上下文 + 定时器线程），
                // 必须销毁，否则每次失败都会泄漏约 7MB native 内存。
                INIT_FAILURES.incrementAndGet();
                com.onefilm.android.tv.utils.SearchDebug.log("init-failed site=" + key + " api=" + api, e);
                if (spider != null) {
                    try {
                        spider.destroy();
                    } catch (Throwable ignored) {
                    }
                }
                return new SpiderNull();
            }
        });
    }

    /** 累计的 JS 引擎初始化失败次数（用于诊断内存泄漏与源兼容性）。 */
    public static int initFailures() {
        return INIT_FAILURES.get();
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }

    /**
     * 回收不在保留集合内的 JS 引擎。
     *
     * <p>聚合搜索会为每个 JS 站点建立一个 QuickJS 运行时（单站点约 7MB），搜索结束后
     * 这些运行时不再使用却一直常驻，2GB 设备上可达 600MB+。这里只保留首页站点与最近
     * 使用的站点，其余销毁；用户点开某个站点的结果时 {@link #getSpider} 会按需重建该
     * 站点引擎（JS 源码命中 Module 缓存，不走网络）。
     *
     * @return 实际销毁的引擎数量
     */
    public int retain(Collection<String> keepKeys) {
        int released = 0;
        long now = System.currentTimeMillis();
        for (String key : new ArrayList<>(spiders.keySet())) {
            if (keepKeys != null && keepKeys.contains(key)) continue;
            if (recent != null && recent.equals(key)) continue;
            Long used = lastAccess.get(key);
            // 最近 20 秒内被取用过（可能在跑详情/播放/proxy），本轮先保留。
            if (used != null && now - used < RETAIN_GRACE_MS) continue;
            Spider spider = spiders.remove(key);
            lastAccess.remove(key);
            if (spider == null) continue;
            released++;
            try {
                spider.destroy();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        android.util.Log.i("JsLoader", "retain released=" + released + " kept=" + spiders.size());
        return released;
    }

    /**
     * 释放单个站点的引擎（方案 B）。
     *
     * <p>聚合搜索里每个 JS 站点只会被调用一次，搜索返回后它的引擎就用不到了，
     * 立刻销毁可以把搜索期峰值从「站点总数 × 7MB」压到「并发数 × 7MB」。
     * 用户随后点开该站点结果时，{@link #getSpider} 会按需重建。
     *
     * @return 是否真的销毁了一个引擎
     */
    public boolean release(String key) {
        if (key == null || key.isEmpty()) return false;
        Long used = lastAccess.get(key);
        // 刚被取用过（可能正在跑详情/播放），先留着，交给后续的批量回收。
        if (used != null && System.currentTimeMillis() - used < RELEASE_GRACE_MS) return false;
        Spider spider = spiders.remove(key);
        lastAccess.remove(key);
        if (spider == null) return false;
        try {
            spider.destroy();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return true;
    }
}
