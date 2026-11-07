package com.onefilm.android.tv.ui.widget;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.catvod.utils.Prefers;
import com.onefilm.android.tv.R;

/**
 * 手机端「广告过滤成功」底部提示。
 *
 * <p>直接挂在 Activity 的内容根布局上，不依赖播放器控件；扁平圆角、无阴影，
 * 5 秒后自动淡出。配色跟随 App 主题：0=简约明亮，1=暗黑，2=跟随系统。
 */
public final class MobileAdFilterTip {

    private static final String TAG = "mobile_ad_filter_tip";
    private static final long VISIBLE_MS = 5_000L;

    private MobileAdFilterTip() {
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;

        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        View previous = root.findViewWithTag(TAG);
        if (previous != null) {
            previous.animate().cancel();
            root.removeView(previous);
        }

        boolean dark = isDarkTheme(activity);
        int background = dark ? 0xF22C2C2E : 0xF2FFFFFF;
        int foreground = dark ? 0xFFF2F2F7 : 0xFF1C1C1E;

        TextView text = new TextView(activity);
        text.setTag(TAG);
        text.setText(R.string.mobile_ad_filter_success_tip);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        text.setTextColor(foreground);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        int paddingH = dp(activity, 16);
        int paddingV = dp(activity, 10);
        text.setPadding(paddingH, paddingV, paddingH, paddingV);
        text.setElevation(0f);

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(activity, 10));
        text.setBackground(shape);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(activity, 24) + bottomInset(activity);
        params.leftMargin = dp(activity, 24);
        params.rightMargin = dp(activity, 24);
        text.setMaxWidth((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.88f));

        root.addView(text, params);

        text.setAlpha(0f);
        text.setTranslationY(dp(activity, 12));
        text.animate().alpha(1f).translationY(0f).setDuration(160L).start();
        text.postDelayed(() -> {
            if (text.getParent() != root) return;
            text.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction(() -> root.removeView(text))
                    .start();
        }, VISIBLE_MS);
    }

    private static boolean isDarkTheme(Context context) {
        int mode = Prefers.getInt("compose_theme_mode", 0);
        if (mode == 0) return false;
        if (mode == 1) return true;
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int bottomInset(Activity activity) {
        try {
            WindowInsetsCompat insets =
                    ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
            if (insets == null) return 0;
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            return bars.bottom;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
