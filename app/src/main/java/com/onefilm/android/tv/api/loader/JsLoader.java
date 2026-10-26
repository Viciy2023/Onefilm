package com.onefilm.android.tv.api.loader;

import com.onefilm.android.tv.App;
import com.fongmi.quickjs.crawler.Loader;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JS spider 缓存：与 {@link PyLoader} 同样改为有上限 LRU。
 *
 * <p>每个 QuickJS spider 都持有独立的 JS 运行时，无上限缓存会在访问大量站点后
 * 把 native 内存吃光（日志里表现为 {@code QuickJSException: out of memory}）。
 */
public class JsLoader {

    private static final int MAX_CACHED = 6;

    private final LinkedHashMap<String, Spider> spiders;
    private final Loader loader;
    private final Object lock = new Object();
    private volatile String recent;

    public JsLoader() {
        spiders = new LinkedHashMap<>(16, 0.75f, true);
        loader = new Loader();
    }

    public void clear() {
        synchronized (lock) {
            for (Spider spider : spiders.values()) destroy(spider);
            Module.get().clear();
            spiders.clear();
            recent = null;
        }
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        synchronized (lock) {
            Spider cached = spiders.get(key);
            if (cached != null) return cached;
            Spider spider;
            try {
                spider = loader.spider(api, BaseLoader.get().dex(jar));
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
