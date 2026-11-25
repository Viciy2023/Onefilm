package com.onefilm.android.tv.utils;

import com.github.catvod.net.OkHttp;
import com.onefilm.android.tv.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads the R2-hosted update manifest.
 *
 * Manifest format follows the existing OneTV/GitHub release contract:
 * {
 *   "versionCode": 2,
 *   "tag_name": "v0.1.1",
 *   "assets": [{ "name": "OneFilm-TV-v0.1.1-arm64-v8a.apk",
 *                "browser_download_url": "https://onefilmapk.../....apk" }],
 *   "body": "release notes"
 * }
 *
 * Both the manifest and the APKs live on Cloudflare R2 so in-app updates do not
 * depend on GitHub download speed; GitHub Releases stay the public download page.
 */
public final class UpdateManifest {

    private UpdateManifest() {
    }

    public static Info fetch() throws Exception {
        return parse(OkHttp.string(BuildConfig.UPDATE_STABLE_URL));
    }

    public static Info parse(String json) throws Exception {
        JSONObject object = new JSONObject(json);
        String version = object.optString("versionName", "").trim();
        if (version.isEmpty()) version = object.optString("tag_name", "").trim();
        if (version.startsWith("v") || version.startsWith("V")) version = version.substring(1);
        int code = object.optInt("versionCode", object.optInt("code", 0));
        String notes = object.optString("body", object.optString("desc", "")).trim();
        String url = pickDownloadUrl(object);
        if (url.isEmpty()) throw new IllegalStateException("更新清单缺少 APK 下载地址");
        return new Info(version, code, notes, url);
    }

    private static String pickDownloadUrl(JSONObject object) {
        String abi = android.os.Process.is64Bit() ? "arm64" : "armeabi";
        // Release assets use TV/Mobile in the filename, while Gradle flavor is
        // still leanback/mobile.
        String flavor = BuildConfig.FLAVOR.equals("leanback") ? "tv" : BuildConfig.FLAVOR;
        String fallback = "";
        JSONArray assets = object.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String url = asset.optString("browser_download_url", asset.optString("url", "")).trim();
                if (!isHttps(url)) continue;
                String name = asset.optString("name", "").toLowerCase(java.util.Locale.ROOT);
                if (name.contains(flavor) && name.contains(abi)) return url;
                if (fallback.isEmpty() && name.contains(abi)) fallback = url;
                if (fallback.isEmpty()) fallback = url;
            }
        }
        if (!fallback.isEmpty()) return fallback;
        String direct = object.optString("url", object.optString("apkUrl", "")).trim();
        return isHttps(direct) ? direct : "";
    }

    private static boolean isHttps(String url) {
        return url != null && url.startsWith("https://");
    }

    public static boolean isNewer(Info info) {
        if (info == null) return false;
        if (info.code > 0) return info.code > BuildConfig.VERSION_CODE;
        return compareVersion(info.version, BuildConfig.VERSION_NAME) > 0;
    }

    private static int compareVersion(String left, String right) {
        String[] a = left.split("[^0-9]+");
        String[] b = right.split("[^0-9]+");
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            int x = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    public static final class Info {
        public final String version;
        public final int code;
        public final String notes;
        public final String url;

        Info(String version, int code, String notes, String url) {
            this.version = version;
            this.code = code;
            this.notes = notes;
            this.url = url;
        }
    }
}
