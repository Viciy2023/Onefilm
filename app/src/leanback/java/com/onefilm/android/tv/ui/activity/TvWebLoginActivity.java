package com.onefilm.android.tv.ui.activity;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.onefilm.android.tv.R;

/**
 * 电视端网盘 / 站点登录页。
 *
 * <p>横屏全屏；遥控器驱动一个虚拟指针（方向键移动、确认键点击），
 * 这样账号密码 / 短信验证码登录这类需要点输入框的页面也能在电视上操作，
 * 点中输入框后由系统输入法接手键盘。
 */
public class TvWebLoginActivity extends WebLoginActivity {

    private View cursor;
    private float cursorX;
    private float cursorY;
    private int step;

    @Override
    protected void onConfigureWindow() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    @Override
    protected boolean pageFocusable() {
        return true;
    }

    @Override
    protected float dialogWidthRatio() {
        return 0.88f;
    }

    @Override
    protected float dialogHeightRatio() {
        return 0.86f;
    }

    @Override
    protected void onPageContainerReady(FrameLayout container) {
        step = dp(36);
        int size = dp(20);
        cursor = new View(this);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0x66FFFFFF);
        background.setStroke(dp(2), 0xFF3B82F6);
        cursor.setBackground(background);
        container.addView(cursor, new FrameLayout.LayoutParams(size, size));
        container.post(() -> {
            cursorX = Math.max(0, (container.getWidth() - size) / 2f);
            cursorY = Math.max(0, (container.getHeight() - size) / 2f);
            applyCursor();
        });
        setHintText(getString(R.string.web_login_tv_hint));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isTyping()) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    move(0, -step);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    move(0, step);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    move(-step, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    move(step, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    tap();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isTyping() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        return manager != null && manager.isAcceptingText();
    }

    private void move(int dx, int dy) {
        View parent = (View) cursor.getParent();
        if (parent == null) return;
        cursorX = clamp(cursorX + dx, 0, parent.getWidth() - cursor.getWidth());
        cursorY = clamp(cursorY + dy, 0, parent.getHeight() - cursor.getHeight());
        applyCursor();
    }

    private void applyCursor() {
        cursor.setX(cursorX);
        cursor.setY(cursorY);
        cursor.bringToFront();
    }

    private void tap() {
        WebView view = webView();
        if (view == null) return;
        float x = cursorX + cursor.getWidth() / 2f;
        float y = cursorY + cursor.getHeight() / 2f;
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        view.dispatchTouchEvent(down);
        view.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
