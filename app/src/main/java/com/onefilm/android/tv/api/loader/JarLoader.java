package com.onefilm.android.tv.api.loader;

import android.content.Context;

import com.onefilm.android.tv.App;
import com.onefilm.android.tv.utils.Download;
import com.onefilm.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

public class JarLoader {

    /** jar spider 数量上限：超过后淘汰最久未使用的实例并 destroy。 */
    private static final int MAX_SPIDERS = 12;

    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, Method> methods;
    private final LinkedHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Object> locks;
    private final Object spiderLock = new Object();
    private volatile String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new LinkedHashMap<>(16, 0.75f, true);
        locks = new ConcurrentHashMap<>();
    }

    public void clear() {
        synchronized (spiderLock) {
            for (Spider spider : spiders.values()) destroy(spider);
            spiders.clear();
        }
        loaders.clear();
        methods.clear();
        locks.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    private void load(String key, File file) {
        if (Thread.interrupted()) return;
        if (!Path.exists(file) || !file.setReadOnly()) return;
        String cachePath = Path.jar().getAbsolutePath();
        DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
        invokeInit(loader);
        invokeProxy(key, loader);
        loaders.put(key, loader);
    }

    private void invokeInit(DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("init", Context.class);
            method.invoke(clz, App.get());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void invokeProxy(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void parseJar(String key, String jar) {
        if (loaders.containsKey(key)) return;
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (loaders.containsKey(key)) return;
            String[] texts = jar.split(";md5;");
            String md5 = texts.length > 1 ? texts[1].trim() : "";
            if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
            jar = texts[0];
            if (!md5.isEmpty() && Crypto.equals(Path.jar(jar), md5)) {
                load(key, Path.jar(jar));
            } else if (jar.startsWith("http")) {
                load(key, Download.create(jar, Path.jar(jar)).get());
            } else if (jar.startsWith("file")) {
                load(key, Path.local(jar));
            }
        }
    }

    public DexClassLoader dex(String jar) {
        try {
            String jaKey = Crypto.md5(jar);
            parseJar(jaKey, jar);
            return loaders.get(jaKey);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        String jaKey = Crypto.md5(jar);
        String spKey = jaKey + key;
        synchronized (spiderLock) {
            Spider cached = spiders.get(spKey);
            if (cached != null) return cached;
            Spider spider;
            try {
                parseJar(jaKey, jar);
                DexClassLoader loader = loaders.get(jaKey);
                if (loader == null) return new SpiderNull();
                spider = (Spider) loader.loadClass("com.github.catvod.spider." + api.split("csp_")[1]).newInstance();
                spider.siteKey = key;
                spider.init(App.get(), ext);
            } catch (Throwable e) {
                e.printStackTrace();
                spider = new SpiderNull();
            }
            spiders.put(spKey, spider);
            evictLocked();
            return spider;
        }
    }

    private void evictLocked() {
        while (spiders.size() > MAX_SPIDERS) {
            Iterator<Map.Entry<String, Spider>> iterator = spiders.entrySet().iterator();
            Map.Entry<String, Spider> victim = null;
            while (iterator.hasNext()) {
                Map.Entry<String, Spider> entry = iterator.next();
                // 正在使用的 jar（recent 是 jar md5）不淘汰，避免打断代理播放
                if (entry.getKey().startsWith(recent)) continue;
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

    private DexClassLoader requireRecentLoader() {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) throw new IllegalStateException("No jar loaded for recent key: " + recent);
        return loader;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Json" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
        return (JSONObject) method.invoke(null, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Mix" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
        return (JSONObject) method.invoke(null, jxs, name, flag, url);
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Method method = recent != null ? methods.get(recent) : null;
        Object[] result = proxyInvoke(method, params);
        if (result != null) return result;
        return tryOthers(params);
    }

    private Object[] tryOthers(Map<String, String> p) {
        return methods.entrySet().stream().filter(e -> !e.getKey().equals(recent)).map(e -> proxyInvoke(e.getValue(), p)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Object[] proxyInvoke(Method method, Map<String, String> params) {
        try {
            return method == null ? null : (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
