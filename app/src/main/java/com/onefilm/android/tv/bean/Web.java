package com.onefilm.android.tv.bean;

import android.net.Uri;
import android.text.TextUtils;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 站点返回的「网页登录」配置。
 *
 * <pre>
 * "web": {
 *   "url": "https://pan.quark.cn/...",          // 登录页
 *   "mode": "browser" | "view",                 // 可选
 *   "cookie": [{ "url": "...", "keys": ["__puus"] }]  // 出现这些 cookie 即视为登录完成
 * }
 * </pre>
 *
 * 移植自上游 FongMi TV 的同名类。
 */
public class Web {

    @SerializedName("url")
    private String url;
    @SerializedName("mode")
    private String mode;
    @SerializedName("cookie")
    private List<Cookie> cookie;

    public static Web objectFrom(String json) {
        if (TextUtils.isEmpty(json)) return null;
        try {
            Web web = com.onefilm.android.tv.App.gson().fromJson(json, Web.class);
            return web != null && web.isValid() ? web : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public String getMode() {
        return mode == null ? "" : mode;
    }

    /** view：站内独立页面，允许页面自己关闭，不跳外部浏览器。 */
    public boolean isView() {
        return "view".equals(mode);
    }

    public boolean hasCookies() {
        return cookie != null && !cookie.isEmpty();
    }

    public boolean isValid() {
        if (!isHttpUrl(url)) return false;
        if (mode != null && !"browser".equals(mode) && !"view".equals(mode)) return false;
        if (cookie == null) return true;
        return cookie.stream().allMatch(this::isValidCookie);
    }

    private boolean isValidCookie(Cookie group) {
        if (group == null) return false;
        if (group.url == null) group.url = url;
        return isHttpUrl(group.url) && group.keys != null && !group.keys.isEmpty() && group.keys.stream().allMatch(Web::validKey);
    }

    /** 登录前记录一次 cookie 快照，之后靠「值变化 + 非空」判断登录完成。 */
    public void snapshot(Function<String, String> reader) {
        if (cookie == null) return;
        for (Cookie group : cookie) snapshot(group, reader);
    }

    private void snapshot(Cookie group, Function<String, String> reader) {
        Map<String, String> current = values(reader.apply(group.url));
        group.initial = new HashMap<>();
        for (String key : group.keys) group.initial.put(key, current.getOrDefault(key, ""));
    }

    public boolean isComplete(String page, Function<String, String> reader) {
        return cookie != null && cookie.stream().anyMatch(group -> isComplete(group, page, reader));
    }

    /**
     * 不校验当前页面域名的完成判定。
     *
     * <p>部分登录页成功后停留在 SSO 域（例如夸克停在 id.uc.cn），但 cookie 已经写到业务域，
     * 此时仍应视为登录完成。
     */
    public boolean isCompleteLoose(Function<String, String> reader) {
        if (cookie == null) return false;
        for (Cookie group : cookie) {
            if (group.initial == null) continue;
            Map<String, String> current = values(reader.apply(group.url));
            boolean ready = true;
            boolean changed = false;
            for (String key : group.keys) {
                String value = current.getOrDefault(key, "");
                ready &= !value.isEmpty();
                changed |= !value.equals(group.initial.getOrDefault(key, ""));
            }
            if (ready && changed) return true;
        }
        return false;
    }

    private boolean isComplete(Cookie group, String page, Function<String, String> reader) {
        if (group.initial == null || !sameOrigin(group.url, page)) return false;
        Map<String, String> current = values(reader.apply(group.url));
        boolean ready = true;
        boolean changed = false;
        for (String key : group.keys) {
            String value = current.getOrDefault(key, "");
            ready &= !value.isEmpty();
            changed |= !value.equals(group.initial.getOrDefault(key, ""));
        }
        return ready && changed;
    }

    /** 需要保存 / 轮询 cookie 的地址列表（每组的 url，缺省用登录页地址）。 */
    public List<String> cookieUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(getUrl());
        if (cookie == null) return urls;
        for (Cookie group : cookie) {
            String value = TextUtils.isEmpty(group.url) ? url : group.url;
            if (!TextUtils.isEmpty(value) && !urls.contains(value)) urls.add(value);
        }
        return urls;
    }

    private static boolean isHttpUrl(String value) {
        if (TextUtils.isEmpty(value)) return false;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }

    private static boolean validKey(String key) {
        if (TextUtils.isEmpty(key)) return false;
        for (int i = 0; i < key.length(); i++) {
            char value = key.charAt(i);
            if (value <= 0x20 || value >= 0x7F || "()<>@,;:\\\"/[]?={}".indexOf(value) >= 0) return false;
        }
        return true;
    }

    private static boolean sameOrigin(String url, String page) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(page)) return false;
        Uri origin = Uri.parse(url);
        Uri current = Uri.parse(page);
        String originHost = origin.getHost();
        String currentHost = current.getHost();
        if (originHost == null || currentHost == null) return false;
        String originScheme = origin.getScheme();
        String currentScheme = current.getScheme();
        if (originScheme == null || currentScheme == null) return false;
        return originScheme.equalsIgnoreCase(currentScheme)
                && originHost.equalsIgnoreCase(currentHost)
                && port(origin) == port(current);
    }

    private static int port(Uri uri) {
        int port = uri.getPort();
        if (port != -1) return port;
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static Map<String, String> values(String header) {
        if (TextUtils.isEmpty(header)) return Collections.emptyMap();
        Map<String, String> values = new HashMap<>();
        for (String item : header.split(";")) {
            int separator = item.indexOf('=');
            if (separator > 0) values.put(item.substring(0, separator).trim(), item.substring(separator + 1).trim());
        }
        return values;
    }

    public static class Cookie {
        @SerializedName("url")
        private String url;
        @SerializedName("keys")
        private List<String> keys;
        private transient Map<String, String> initial;

        public String getUrl() {
            return url;
        }

        public List<String> getKeys() {
            return keys == null ? Collections.emptyList() : keys;
        }
    }
}
