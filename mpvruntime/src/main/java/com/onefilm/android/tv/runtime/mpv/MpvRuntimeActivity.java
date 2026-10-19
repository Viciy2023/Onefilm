package com.onefilm.android.tv.runtime.mpv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Self-test: binds the runtime service and renders an HTTP video through libmpv. */
public class MpvRuntimeActivity extends Activity implements SurfaceHolder.Callback {

    private static final String URL = "http://127.0.0.1:8000/sample-ch.mkv";

    private IMpvRuntime runtime;
    private SurfaceView surfaceView;
    private TextView status;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            runtime = IMpvRuntime.Stub.asInterface(service);
            status.setText("service connected");
            if (surfaceView.getHolder().getSurface() != null
                    && surfaceView.getHolder().getSurface().isValid()) {
                startPlayback(surfaceView.getHolder());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            runtime = null;
            status.setText("service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        status = new TextView(this);
        status.setTextSize(16f);
        status.setPadding(24, 24, 24, 24);
        root.addView(surfaceView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        Intent intent = new Intent(this, MpvRuntimeService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void startPlayback(SurfaceHolder holder) {
        try {
            runtime.setSurface(holder.getSurface());
            runtime.open(URL);
            runtime.play();
            status.setText("playing " + URL);
        } catch (Exception e) {
            status.setText("play failed: " + e);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (runtime != null) startPlayback(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        try {
            if (runtime != null) runtime.setSurface(null);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (runtime != null) runtime.release();
        } catch (Exception ignored) {
        }
        unbindService(connection);
        super.onDestroy();
    }
}
