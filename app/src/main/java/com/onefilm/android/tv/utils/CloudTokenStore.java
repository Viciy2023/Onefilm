package com.onefilm.android.tv.utils;

import android.text.TextUtils;

import com.github.catvod.utils.Prefers;

/**
 * 云盘网页登录时从 localStorage 抓到的 token（目前只有阿里云盘需要）。
 *
 * <p>按 host 保存，读取时支持父域回退，逻辑与 {@link com.github.catvod.utils.CookieStore} 一致。
 */
public final class CloudTokenStore {

    private static final String PREFIX = "cloud_token_";
    private static final String DRIVE_ID = "cloud_ali_drive_id";

    private CloudTokenStore() {
    }

    public static String getByHost(String host) {
        if (TextUtils.isEmpty(host)) return "";
        String current = host.toLowerCase();
        while (true) {
            String value = Prefers.getString(PREFIX + current);
            if (!TextUtils.isEmpty(value)) return value;
            int dot = current.indexOf('.');
            if (dot < 0) return "";
            String parent = current.substring(dot + 1);
            if (!parent.contains(".")) return "";
            current = parent;
        }
    }

    public static void putByHost(String host, String value) {
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(value)) return;
        Prefers.put(PREFIX + host.toLowerCase(), value);
    }

    public static void clear(String host) {
        if (!TextUtils.isEmpty(host)) Prefers.remove(PREFIX + host.toLowerCase());
        Prefers.remove(DRIVE_ID);
    }

    public static String getDriveId() {
        return Prefers.getString(DRIVE_ID);
    }

    public static void putDriveId(String driveId) {
        if (TextUtils.isEmpty(driveId)) return;
        Prefers.put(DRIVE_ID, driveId);
    }
}
