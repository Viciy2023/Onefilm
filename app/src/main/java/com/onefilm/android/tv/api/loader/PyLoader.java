package com.onefilm.android.tv.api.loader;

import com.onefilm.android.tv.App;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python spider 缓存。
 *
 * <p>每个 spider 都持有一份 Python 解释器/模块，属于 native 内存大户。原先用
 * ConcurrentHashMap 无上限缓存，访问站点越多占用越高，最终在 32 位盒子上触发
 * {@code std::bad_alloc} → SIGABRT。
 *
 * <p>这里改成有上限的 LRU：只保留最近使用的若干个，淘汰时调用 destroy() 释放
 * 解释器；最近正在使用的 spider（recent）不淘汰，避免打断代理播放。
 */
public class PyLoader {

    private static final int MAX_CACHED = 6;

    private final LinkedHashMap<String, Spider> spiders;
    private final Loader loader;
    private final Object lock = new Object();
    private volatile String recent;

    public PyLoader() {
        spiders = new LinkedHashMap<>(16, 0.75f, true);
        loader = new Loader();
    }

    public void clear() {
        synchronized (lock) {
            for (Spider spider : spiders.values()) destroy(spider);
            spiders.clear();
            recent = null;
        }
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        synchronized (lock) {
            Spider cached = spiders.get(key);
            if (cached != null) return cached;
            Spider spider;
            try {
                spider = loader.spider(api);
                spider.siteKey = key;
                spider.init(App.get(), ext);
            } catch (Throwable e) {
                e.printStackTrace();
                spider = new SpiderNull();
            }
            spiders.put(key, spider);
            evictLocked();
            return spider;
        }
    }

    private void evictLocked() {
        while (spiders.size() > MAX_CACHED) {
            Iterator<Map.Entry<String, Spider>> iterator = spiders.entrySet().iterator();
            Map.Entry<String, Spider> victim = null;
            while (iterator.hasNext()) {
                Map.Entry<String, Spider> entry = iterator.next();
                if (entry.getKey().equals(recent)) continue;
                victim = entry;
                break;
            }
            if (victim == null) break;
            iterator.remove();
            destroy(victim.getValue());
        }
    }

    private static void destroy(Spider spider) {
        try {
            spider.destroy();
        } catch (Throwable ignored) {
        }
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        synchronized (lock) {
            if (recent == null) return null;
            Spider spider = spiders.get(recent);
            return spider != null ? spider.proxy(params) : null;
        }
    }
}
