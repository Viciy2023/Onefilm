package com.onefilm.android.tv.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.onefilm.android.tv.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 崩溃与诊断落盘。
 *
 * <p>盒子上的崩溃过去只能靠 ADB 复现；这里把最近一次未捕获异常的堆栈写到
 * 应用私有目录，并尽力复制一份到 Download 目录，方便用户导出。
 *
 * <p>写入本身可能发生在异常路径上（包括线程创建失败的 OOM），因此所有方法
 * 都必须吞掉自身异常，绝不能因为记录日志而再次崩溃。
 */
public final class CrashLog {

    private static final String TAG = "OneFilm";
    private static final String LAST_NAME = "last.txt";
    private static final String PUBLIC_NAME = "onefilm-crash-last.txt";

    private CrashLog() {
    }

    /** 记录一次未捕获异常；失败时静默。 */
    public static void write(Context context, Thread thread, Throwable error) {
        if (context == null || error == null) return;
        try {
            String text = buildReport(thread, error);
            File dir = new File(context.getFilesDir(), "crash");
            if (!dir.exists()) dir.mkdirs();
            writeFile(new File(dir, LAST_NAME), text);
            try {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (downloads != null && (downloads.exists() || downloads.mkdirs())) {
                    writeFile(new File(downloads, PUBLIC_NAME), text);
                }
            } catch (Throwable ignored) {
                // 外置存储不可写时只保留私有副本
            }
        } catch (Throwable e) {
            Log.w(TAG, "write crash log failed", e);
        }
    }

    /** 记录一条诊断备注（不覆盖最近一次崩溃）。 */
    public static void note(Context context, String message) {
        if (context == null || message == null) return;
        try {
            File dir = new File(context.getFilesDir(), "crash");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "notes.txt");
            String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "  " + message + "\n";
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8")) {
                writer.write(line);
            }
        } catch (Throwable e) {
            Log.w(TAG, "write crash note failed", e);
        }
    }

    /** 把最近一次崩溃 + 当前环境信息导出到 Download，返回文件路径；失败返回空串。 */
    public static String export(Context context) {
        if (context == null) return "";
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            String name = "onefilm-diagnostics-" + stamp + ".txt";
            String report = buildEnv(context) + "\n\n" + readLast(context);
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File target = null;
            if (downloads != null && (downloads.exists() || downloads.mkdirs())) {
                target = new File(downloads, name);
                writeFile(target, report);
            }
            if (target == null) {
                File fallback = new File(context.getExternalFilesDir(null), name);
                writeFile(fallback, report);
                target = fallback;
            }
            return target.getAbsolutePath();
        } catch (Throwable e) {
            Log.w(TAG, "export diagnostics failed", e);
            return "";
        }
    }

    private static String buildReport(Thread thread, Throwable error) {
        StringBuilder sb = new StringBuilder();
        sb.append("time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        sb.append("version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("thread: ").append(thread == null ? "?" : thread.getName()).append('\n');
        sb.append("exception: ").append(error).append('\n');
        for (StackTraceElement element : error.getStackTrace()) {
            sb.append("  at ").append(element).append('\n');
        }
        Throwable cause = error.getCause();
        if (cause != null) {
            sb.append("cause: ").append(cause).append('\n');
            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append("  at ").append(element).append('\n');
            }
        }
        return sb.toString();
    }

    private static String buildEnv(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("OneFilm diagnostics\n");
        sb.append("time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        sb.append("version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("flavor: ").append(BuildConfig.FLAVOR).append('\n');
        sb.append("model: ").append(Build.MODEL).append('\n');
        sb.append("device: ").append(Build.DEVICE).append('\n');
        sb.append("sdk: ").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("abi: ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?").append('\n');
        sb.append("threads: ").append(Thread.activeCount()).append('\n');
        return sb.toString();
    }

    private static String readLast(Context context) {
        try {
            File file = new File(new File(context.getFilesDir(), "crash"), LAST_NAME);
            if (!file.exists()) return "(no crash recorded)";
            byte[] buffer = new byte[(int) Math.min(file.length(), 64 * 1024)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int read = in.read(buffer);
                return read <= 0 ? "(empty)" : new String(buffer, 0, read, "UTF-8");
            }
        } catch (Throwable e) {
            return "(read failed: " + e + ")";
        }
    }

    private static void writeFile(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, false), "UTF-8")) {
            writer.write(text);
        }
    }
}
