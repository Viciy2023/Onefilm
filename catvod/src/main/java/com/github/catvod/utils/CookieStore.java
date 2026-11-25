package com.github.catvod.utils;

import android.net.Uri;
import android.text.TextUtils;

/**
 * 站点 / 网盘 cookie 存储（按 host 保存）。
 *
 * <p>用途：用户在「网盘 - 云盘账号」里扫码登录后，把夸克/阿里等账号的 cookie 存到这里；
 * spider 通过 {@code java.getCookie(url)} 读取，或在发请求时由网络层自动带上。
 *
 * <p>只存本机 Prefers，不上传任何服务器。
 */
public final class CookieStore {

    private CookieStore() {
    }

    private static String key(String host) {
        return "cookie_" + host;
    }

    /** 取 url 对应的 host（小写）；解析失败返回空串。 */
    public static String hostOf(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host.toLowerCase();
        } catch (Throwable e) {
            return "";
        }
    }

    public static String get(String url) {
        String host = hostOf(url);
        return TextUtils.isEmpty(host) ? "" : getByHost(host);
    }

    /**
     * 按域名取 cookie：先精确匹配，再逐级向上匹配父域。
     *
     * <p>例：登录页存的是 {@code pan.quark.cn}，而接口域名是 {@code drive-pc.quark.cn}，
     * 两者共享 {@code .quark.cn} 域 cookie，需要父域回退才能取到。
     */
    public static String getByHost(String host) {
        if (TextUtils.isEmpty(host)) return "";
        String current = host.toLowerCase();
        if (isIp(current)) return Prefers.getString(key(current));
        while (true) {
            String value = Prefers.getString(key(current));
            if (!TextUtils.isEmpty(value)) return value;
            int dot = current.indexOf('.');
            if (dot < 0) return "";
            String parent = current.substring(dot + 1);
            if (!parent.contains(".")) return "";
            current = parent;
        }
    }

    private static boolean isIp(String host) {
        for (int i = 0; i < host.length(); i++) {
            char value = host.charAt(i);
            if (value != '.' && (value < '0' || value > '9')) return false;
        }
        return true;
    }

    public static void put(String url, String cookie) {
        putByHost(hostOf(url), cookie);
    }

    public static void putByHost(String host, String cookie) {
        if (TextUtils.isEmpty(host)) return;
        Prefers.put(key(host.toLowerCase()), cookie == null ? "" : cookie);
    }

    public static void remove(String host) {
        putByHost(host, "");
    }

    public static boolean has(String url) {
        return !TextUtils.isEmpty(get(url));
    }
}
