package com.onefilm.android.tv.player.media;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MediaChapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

/** Loads Matroska chapters using a bounded HTTP range / local prefix read. */
public final class ChapterLoader {

    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onLoaded(List<MediaChapter> chapters);
    }

    private ChapterLoader() {
    }

    public static void load(Context context, Uri uri, Callback callback) {
        if (uri == null) {
            deliver(callback, Collections.emptyList());
            return;
        }
        String scheme = uri.getScheme();
        if (scheme == null || "http".equals(scheme) || "https".equals(scheme)) {
            loadHttp(uri, callback);
        } else {
            new Thread(() -> deliver(callback, readLocal(context, uri)), "chapter-local").start();
        }
    }

    private static void loadHttp(Uri uri, Callback callback) {
        new Thread(() -> {
            List<MediaChapter> chapters = Collections.emptyList();
            Request request = new Request.Builder()
                    .url(uri.toString())
                    .header("Range", "bytes=0-" + (MAX_BYTES - 1))
                    .build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.body() != null) {
                    BufferedSource source = response.body().source();
                    byte[] buffer = new byte[MAX_BYTES];
                    int read = 0;
                    while (read < buffer.length) {
                        int n = source.read(buffer, read, buffer.length - read);
                        if (n < 0) break;
                        read += n;
                    }
                    byte[] data = new byte[read];
                    System.arraycopy(buffer, 0, data, 0, read);
                    chapters = MkvChapterParser.parse(data);
                }
            } catch (Exception ignored) {
            }
            deliver(callback, chapters);
        }, "chapter-http").start();
    }

    private static List<MediaChapter> readLocal(Context context, Uri uri) {
        try (InputStream input = open(context, uri)) {
            if (input == null) return Collections.emptyList();
            byte[] buffer = new byte[MAX_BYTES];
            int read = 0;
            while (read < buffer.length) {
                int n = input.read(buffer, read, buffer.length - read);
                if (n < 0) break;
                read += n;
            }
            byte[] data = new byte[read];
            System.arraycopy(buffer, 0, data, 0, read);
            return MkvChapterParser.parse(data);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static InputStream open(Context context, Uri uri) throws Exception {
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) return new FileInputStream(new File(uri.getPath()));
        return context.getContentResolver().openInputStream(uri);
    }

    private static void deliver(Callback callback, List<MediaChapter> chapters) {
        if (callback == null) return;
        MAIN.post(() -> callback.onLoaded(chapters));
    }
}
