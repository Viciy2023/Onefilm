package com.onefilm.android.tv.utils;

import android.util.Log;

import com.onefilm.android.tv.App;

import java.io.File;
import java.io.FileWriter;

/**
 * 聚合搜索的诊断日志。
 *
 * <p>写到 App 外部目录的 {@code search-debug.log}，避免被 logcat 环形缓冲区冲掉
 * （聚合搜索每个源都会打印完整结果 JSON，日志量极大）。
 *
 * <p>仅用于问题定位，正式发版前应整体移除。
 */
public final class SearchDebug {

    private static final String FILE = "search-debug.log";

    private SearchDebug() {
    }

    public static void log(String msg) {
        Log.i("SearchDebug", msg);
        try {
            File dir = App.get().getExternalFilesDir(null);
            if (dir == null) return;
            try (FileWriter writer = new FileWriter(new File(dir, FILE), true)) {
                writer.append(String.valueOf(System.currentTimeMillis())).append(' ').append(msg).append('\n');
            }
        } catch (Throwable ignored) {
        }
    }

    public static void log(String msg, Throwable error) {
        log(msg + " err=" + oneLine(Log.getStackTraceString(error)));
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replace('\n', '|').replace('\r', ' ');
    }
}
