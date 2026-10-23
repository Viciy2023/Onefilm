package com.onefilm.android.tv.utils;

/**
 * GitHub is only the APK distribution channel for OneFilm/壹影.
 * Update metadata is hosted on R2 (see BuildConfig.UPDATE_STABLE_URL).
 */
public class Github {

    /** Replace this if the public repository is renamed. */
    public static final String REPO = "Viciy2023/Onefilm";
    public static final String RELEASES = "https://github.com/" + REPO + "/releases";

    public static String releaseUrl(String tag) {
        return RELEASES + "/tag/" + tag;
    }

    public static String updateManifestUrl() {
        return com.onefilm.android.tv.BuildConfig.UPDATE_STABLE_URL;
    }
}
