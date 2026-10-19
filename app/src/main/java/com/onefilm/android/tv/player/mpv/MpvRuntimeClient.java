package com.onefilm.android.tv.player.mpv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.view.Surface;

import com.onefilm.android.tv.runtime.mpv.IMpvRuntime;

/** Binds the standalone MPV runtime APK and forwards playback commands. */
public final class MpvRuntimeClient {

    private static final String PACKAGE = "com.onefilm.android.tv.runtime.mpv";
    private static final String SERVICE = "com.onefilm.android.tv.runtime.mpv.MpvRuntimeService";

    public interface Listener {
        void onConnected();

        void onDisconnected();
    }

    private final Context context;
    private final Listener listener;
    private IMpvRuntime runtime;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            runtime = IMpvRuntime.Stub.asInterface(service);
            if (listener != null) listener.onConnected();
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

    public boolean bind() {
        if (bound) return true;
        Intent intent = new Intent("com.onefilm.android.tv.runtime.mpv.BIND");
        intent.setPackage(PACKAGE);
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            Intent explicit = new Intent();
            explicit.setComponent(new ComponentName(PACKAGE, SERVICE));
            bound = context.bindService(explicit, connection, Context.BIND_AUTO_CREATE);
        }
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

    private IMpvRuntime runtime() {
        return runtime;
    }

    public void setSurface(Surface surface) {
        try {
            if (runtime() != null) runtime().setSurface(surface);
        } catch (Exception ignored) {
        }
    }

    public void open(String url) {
        try {
            if (runtime() != null) runtime().open(url);
        } catch (Exception ignored) {
        }
    }

    public void play() {
        try {
            if (runtime() != null) runtime().play();
        } catch (Exception ignored) {
        }
    }

    public void pause() {
        try {
            if (runtime() != null) runtime().pause();
        } catch (Exception ignored) {
        }
    }

    public void seekTo(long positionMs) {
        try {
            if (runtime() != null) runtime().seekTo(positionMs);
        } catch (Exception ignored) {
        }
    }

    public long getPosition() {
        try {
            return runtime() == null ? 0L : runtime().getPosition();
        } catch (Exception e) {
            return 0L;
        }
    }

    public long getDuration() {
        try {
            return runtime() == null ? 0L : runtime().getDuration();
        } catch (Exception e) {
            return 0L;
        }
    }

    public void release() {
        try {
            if (runtime() != null) runtime().release();
        } catch (Exception ignored) {
        }
        unbind();
    }
}
