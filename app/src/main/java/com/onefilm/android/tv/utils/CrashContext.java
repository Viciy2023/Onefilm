package com.onefilm.android.tv.utils;

/**
 * 崩溃上报时要附带的轻量上下文。
 *
 * <p>只在内存里存两个字符串，崩溃回调里直接读，不做任何 IO —— 文档明确要求
 * CrashHandleCallback 的逻辑必须简单、稳定，否则会影响崩溃上报本身。
 */
public final class CrashContext {

    private static volatile String configUrl = "";
    private static volatile String lastSearch = "";

    private CrashContext() {
    }

    public static void setConfigUrl(String value) {
        configUrl = value == null ? "" : value;
    }

    public static void setLastSearch(String value) {
        lastSearch = value == null ? "" : value;
    }

    public static String getConfigUrl() {
        return configUrl;
    }

    public static String getLastSearch() {
        return lastSearch;
    }
}
