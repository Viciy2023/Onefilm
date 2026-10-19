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
        public void setDolbyVisionMode(int mode) {
            String value = mode == 3 ? "yes" : (mode == 4 ? "no" : "auto");
            android.util.Log.i("MpvRuntime", "dolby-vision-output=" + value);
            MPVLib.INSTANCE.setPropertyString("android-dolby-vision-output", value);
        }

        @Override
        public void seekTo(long positionMs) {
            double seconds = positionMs / 1000.0;
            Double before = MPVLib.INSTANCE.getPropertyDouble("time-pos");
            android.util.Log.i("MpvRuntime", "seek before=" + before + " target=" + seconds);
            MPVLib.INSTANCE.command("seek", String.valueOf(seconds), "absolute");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                Double after = MPVLib.INSTANCE.getPropertyDouble("time-pos");
                Boolean seeking = MPVLib.INSTANCE.getPropertyBoolean("seeking");
                android.util.Log.i("MpvRuntime", "seek after=" + after + " seeking=" + seeking);
            }, 600);
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
