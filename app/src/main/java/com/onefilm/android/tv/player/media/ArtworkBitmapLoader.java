package com.onefilm.android.tv.player.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.util.BitmapLoader;

import com.bumptech.glide.load.model.GlideUrl;
import com.onefilm.android.tv.utils.ImgUtil;
import com.onefilm.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * OneFilm compatibility shim for the upstream DataSourceBitmapLoader.
 * Phase 1 decodes artwork locally instead of using the media3-session loader.
 */
public final class ArtworkBitmapLoader implements BitmapLoader {

    private static final long MAX_ARTWORK_BYTES = 16L * 1024 * 1024;

    private final Context context;

    public ArtworkBitmapLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean supportsMimeType(@NonNull String mimeType) {
        return mimeType.startsWith("image/");
    }

    @NonNull
    @Override
    public ListenableFuture<Bitmap> decodeBitmap(@NonNull byte[] data) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bitmap == null) return Futures.immediateFailedFuture(new IOException("Unsupported artwork"));
        return Futures.immediateFuture(bitmap);
    }

    @NonNull
    @Override
    public ListenableFuture<Bitmap> loadBitmap(@NonNull Uri uri) {
        try {
            Object model = ImgUtil.getUrl(uri.toString());
            if (!(model instanceof GlideUrl url)) return decodeUri(uri);
            String scheme = UrlUtil.scheme(url.toStringUrl());
            if (!"http".equals(scheme) && !"https".equals(scheme)) return decodeUri(uri);
            return Futures.transformAsync(loadBytes(url), data -> decodeBitmap(Objects.requireNonNull(data)), MoreExecutors.directExecutor());
        } catch (RuntimeException error) {
            return Futures.immediateFailedFuture(error);
        }
    }

    private ListenableFuture<Bitmap> decodeUri(Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return Futures.immediateFailedFuture(new IOException("Cannot open " + uri));
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) return Futures.immediateFailedFuture(new IOException("Unsupported artwork"));
            return Futures.immediateFuture(bitmap);
        } catch (Exception error) {
            return Futures.immediateFailedFuture(error);
        }
    }

    private static ListenableFuture<byte[]> loadBytes(@NonNull GlideUrl url) {
        Call call = OkHttp.newCall(url.toStringUrl(), url.getHeaders());
        SettableFuture<byte[]> future = SettableFuture.create();
        future.addListener(() -> {
            if (future.isCancelled()) call.cancel();
        }, MoreExecutors.directExecutor());
        call.enqueue(new ArtworkCallback(future));
        return future;
    }

    private static byte[] readBytes(@NonNull Response response) throws IOException {
        if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " " + response.message());
        if (response.body().contentLength() > MAX_ARTWORK_BYTES) throw new IOException("Artwork size limit exceeded");
        byte[] data = ByteStreams.toByteArray(ByteStreams.limit(response.body().byteStream(), MAX_ARTWORK_BYTES + 1));
        if (data.length == 0) throw new IOException("Empty artwork");
        if (data.length > MAX_ARTWORK_BYTES) throw new IOException("Artwork size limit exceeded");
        return data;
    }

    private record ArtworkCallback(SettableFuture<byte[]> future) implements Callback {

        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
            future.setException(e);
        }

        @Override
        public void onResponse(@NonNull Call call, @NonNull Response response) {
            try (response) {
                future.set(readBytes(response));
            } catch (Exception e) {
                future.setException(e);
            }
        }
    }
}
