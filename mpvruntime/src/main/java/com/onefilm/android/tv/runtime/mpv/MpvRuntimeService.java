package com.onefilm.android.tv.runtime.mpv;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.Surface;

import androidx.annotation.Nullable;

import is.xyz.mpv.MPVLib;

/** Owns libmpv; main app renders by passing a Surface over Binder. */
public class MpvRuntimeService extends Service {

    private final IMpvRuntime.Stub binder = new IMpvRuntime.Stub() {
        @Override
        public void setSurface(Surface surface) {
            android.util.Log.i("MpvRuntime", "setSurface valid=" + (surface != null && surface.isValid()));
            if (surface == null) {
                MPVLib.INSTANCE.detachSurface();
            } else {
                MPVLib.INSTANCE.attachSurface(surface);
            }
        }

        @Override
        public void open(String url) {
            MPVLib.INSTANCE.command("loadfile", url, "replace");
        }

        @Override
        public void play() {
            MPVLib.INSTANCE.setPropertyBoolean("pause", false);
        }

        @Override
        public void pause() {
            MPVLib.INSTANCE.setPropertyBoolean("pause", true);
        }

        @Override
        public void stop() {
            MPVLib.INSTANCE.command("stop");
        }

        @Override
        public void seekTo(long positionMs) {
            MPVLib.INSTANCE.setPropertyDouble("time-pos", positionMs / 1000.0);
        }

        @Override
        public long getPosition() {
            Double value = MPVLib.INSTANCE.getPropertyDouble("time-pos");
            return value == null ? 0L : (long) (value * 1000);
        }

        @Override
        public long getDuration() {
            Double value = MPVLib.INSTANCE.getPropertyDouble("duration");
            return value == null ? 0L : (long) (value * 1000);
        }

        @Override
        public void release() {
            MPVLib.INSTANCE.detachSurface();
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        MPVLib.INSTANCE.create(this);
        MPVLib.INSTANCE.init();
        MPVLib.INSTANCE.setPropertyString("vo", "gpu");
        MPVLib.INSTANCE.setPropertyString("gpu-context", "android");
        MPVLib.INSTANCE.setPropertyString("gpu-api", "opengl");
        MPVLib.INSTANCE.setPropertyString("hwdec", "auto-safe");
        MPVLib.INSTANCE.setPropertyString("keep-open", "yes");
    }

    @Override
    public void onDestroy() {
        MPVLib.INSTANCE.destroy();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
