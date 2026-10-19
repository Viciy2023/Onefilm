package com.onefilm.android.tv.runtime.mpv;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Standalone MPV runtime module: owns libmpv and its private ffmpeg. */
public class MpvRuntimeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setTextSize(18f);
        text.setPadding(32, 64, 32, 32);
        boolean loaded;
        try {
            System.loadLibrary("mpv");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
            text.setText("libmpv load failed: " + t);
        }
        if (loaded) {
            text.setText("OneFilm MPV runtime\nlibmpv loaded");
        }
        setContentView(text);
    }
}
