package com.onefilm.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.onefilm.android.tv.R;
import com.onefilm.android.tv.compose.host.ComposeMainActivity;
import com.onefilm.android.tv.ui.custom.SplashProgressView;

/** OneFilm: launcher router between the Compose UI and the legacy XML UI. */
public class EntryActivity extends Activity {

    private static final long SPLASH_DURATION_MS = 600L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable routeRunnable = this::routeToHome;
    private boolean routed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        window.getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(createSplashView());
        handler.postDelayed(routeRunnable, SPLASH_DURATION_MS);
    }

    private FrameLayout createSplashView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.splash_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER);
        root.addView(logo, logoParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);

        SplashProgressView progress = new SplashProgressView(this);
        bottom.addView(progress, new LinearLayout.LayoutParams(dp(260), dp(6)));

        TextView hint = new TextView(this);
        hint.setText("正在加载，请稍候！");
        hint.setTextColor(0xFF737373);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(14);
        bottom.addView(hint, hintParams);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottomParams.bottomMargin = dp(64);
        root.addView(bottom, bottomParams);
        return root;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private void routeToHome() {
        if (routed || isFinishing()) {
            return;
        }
        routed = true;
        startActivity(new Intent(this, ComposeMainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(routeRunnable);
        super.onDestroy();
    }
}
