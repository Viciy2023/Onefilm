package com.onefilm.android.tv.player.mpv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import androidx.media3.mpvplayer.MpvPlayer;

import com.onefilm.android.tv.runtime.mpv.IMpvRuntime;

/** Binds the standalone MPV runtime APK and forwards playback commands. */
public final class MpvRuntimeClient implements MpvPlayer.RuntimeBridge {

    private static final String TAG = "MpvClient";
    private static final String PACKAGE = "com.onefilm.android.tv.runtime.mpv";
    private static final String SERVICE = "com.onefilm.android.tv.runtime.mpv.MpvRuntimeService";
    private static final String ACTION = "com.onefilm.android.tv.runtime.mpv.BIND";

    public interface Listener {
        void onConnected();

        void onDisconnected();
    }

    private final Context context;
    private final Listener listener;

    private IMpvRuntime runtime;
    private boolean bound;

    private Surface pendingSurface;
    private String pendingUrl;
    private Boolean pendingPlay;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            runtime = IMpvRuntime.Stub.asInterface(service);
            Log.i(TAG, "connected");
            if (listener != null) listener.onConnected();
            applyPending();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            runtime = null;
            if (listener != null) listener.onDisconnected();
        }
    };

    public MpvRuntimeClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public static boolean isRuntimeInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean bind() {
        if (bound) return true;
        Intent intent = new Intent(ACTION);
        intent.setPackage(PACKAGE);
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            Intent explicit = new Intent();
            explicit.setComponent(new ComponentName(PACKAGE, SERVICE));
            bound = context.bindService(explicit, connection, Context.BIND_AUTO_CREATE);
        }
        Log.i(TAG, "bind=" + bound);
        return bound;
    }

    public void unbind() {
        if (!bound) return;
        bound = false;
        try {
            context.unbindService(connection);
        } catch (RuntimeException ignored) {
        }
        runtime = null;
    }

    public boolean isConnected() {
        return runtime != null;
    }

    private void applyPending() {
        if (runtime == null) return;
        try {
            if (pendingSurface != null) runtime.setSurface(pendingSurface);
            if (pendingUrl != null) {
                Log.i(TAG, "pending open " + pendingUrl);
                runtime.open(pendingUrl);
            }
            if (pendingPlay != null) {
                if (pendingPlay) runtime.play();
                else runtime.pause();
            }
        } catch (Exception e) {
            Log.e(TAG, "applyPending failed", e);
        }
    }

    @Override
    public void setSurface(Surface surface) {
        pendingSurface = surface;
        try {
            if (runtime != null) runtime.setSurface(surface);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void open(String url) {
        pendingUrl = url;
        Log.i(TAG, "open " + url);
        try {
            if (runtime != null) runtime.open(url);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void play() {
        pendingPlay = true;
        try {
            if (runtime != null) runtime.play();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void pause() {
        pendingPlay = false;
        try {
            if (runtime != null) runtime.pause();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void seekTo(long positionMs) {
        try {
            if (runtime != null) runtime.seekTo(positionMs);
        } catch (Exception ignored) {
        }
    }

    @Override
    public long getPosition() {
        try {
            return runtime == null ? 0L : runtime.getPosition();
        } catch (Exception e) {
            return 0L;
        }
    }

    @Override
    public long getDuration() {
        try {
            return runtime == null ? 0L : runtime.getDuration();
        } catch (Exception e) {
            return 0L;
        }
    }

    public void release() {
        try {
            if (runtime != null) runtime.release();
        } catch (Exception ignored) {
        }
        unbind();
    }
}
