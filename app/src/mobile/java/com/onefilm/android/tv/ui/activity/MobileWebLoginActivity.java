package com.onefilm.android.tv.ui.activity;

import android.content.pm.ActivityInfo;

/**
 * 手机端网盘 / 站点登录页：竖屏，页面可触摸操作。
 */
public class MobileWebLoginActivity extends WebLoginActivity {

    @Override
    protected void onConfigureWindow() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
}
