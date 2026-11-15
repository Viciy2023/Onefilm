package com.onefilm.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.onefilm.android.tv.R;
import com.onefilm.android.tv.bean.Web;
import com.onefilm.android.tv.composebridge.AppWebLogin;
import com.onefilm.android.tv.utils.CloudTokenStore;
import com.onefilm.android.tv.utils.Util;
import com.github.catvod.utils.CookieStore;

/**
 * 站点 / 网盘网页登录页（业务壳，两端共用）。
 *
 * <p>TV 与手机各自有独立子类（{@code TvWebLoginActivity} / {@code MobileWebLoginActivity}），
 * 只负责屏幕方向、焦点与提示文案；页面本身、cookie 轮询与保存逻辑都在这里。
 *
 * <p>移植自上游 FongMi TV 的 WebActivity，去掉了解析器相关能力，只保留登录链路。
 */
public class WebLoginActivity extends AppCompatActivity {

    public static final String EXTRA_WEB = "web";

    private static final long CHECK_INTERVAL = 1000L;

    /** 抓 localStorage 里的网盘 token（目前用于阿里云盘）。 */
    private static final String TOKEN_SCRIPT =
            "(function(){try{var out={};var keys=['token','TOKEN','aliyundrive_token'];" +
                    "for(var i=0;i<keys.length;i++){var v=localStorage.getItem(keys[i]);if(v)out[keys[i]]=v;}" +
                    "return JSON.stringify(out);}catch(e){return '';}})()";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loginCheck = this::checkLogin;

    /** 登录页统一用桌面 Chrome UA：移动/电视 UA 下部分登录页会退化成下载引导页。 */
    private static final String LOGIN_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36";

    private WebView webView;
    private TextView hintView;
    private Web options;
    private boolean finished;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        options = Web.objectFrom(getIntent().getStringExtra(EXTRA_WEB));
        if (options == null) {
            finish();
            return;
        }
        onConfigureWindow();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(createContentView());
        // 先记录登录前的 cookie 快照，之后靠「有值且发生变化」判定登录完成。
        options.snapshot(url -> CookieManager.getInstance().getCookie(url));
        webView.loadUrl(options.getUrl());
        startCheck();
    }

    /** 子类设置屏幕方向、系统栏等。 */
    protected void onConfigureWindow() {
    }

    /** 子类追加提示文案（例如电视端提示按返回键退出）。 */
    protected CharSequence hintText() {
        return getString(R.string.web_login_hint);
    }

    /** 电视端不让 WebView 抢焦点，保证遥控器能停留在关闭按钮上。 */
    protected boolean pageFocusable() {
        return true;
    }

    /** 页面容器就绪后回调，子类可在这里叠加自己的控件（例如电视端鼠标指针）。 */
    protected void onPageContainerReady(FrameLayout container) {
    }

    protected WebView webView() {
        return webView;
    }

    protected void setHintText(CharSequence text) {
        if (hintView != null) hintView.setText(text);
    }

    /** 弹窗宽度占比，子类可覆盖（电视端留出更多边距）。 */
    protected float dialogWidthRatio() {
        return 0.96f;
    }

    /** 弹窗高度占比。 */
    protected float dialogHeightRatio() {
        return 0.92f;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private View createContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(view -> close(false));

        int surface = color(com.google.android.material.R.attr.colorSurface, Color.WHITE);
        int onSurface = color(com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        int onSurfaceVariant = color(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(surface);
        cardBackground.setCornerRadius(dp(24));
        card.setBackground(cardBackground);
        card.setClipToOutline(true);
        card.setOnClickListener(view -> {
        });

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        // 关闭多窗口：登录页里 window.open 的跳转直接在当前页面打开，
        // 否则「点击登录没反应」（弹窗被拦），验证码随后过期。
        settings.setSupportMultipleWindows(false);
        settings.setUserAgentString(LOGIN_UA);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        CookieManager manager = CookieManager.getInstance();
        manager.setAcceptCookie(true);
        manager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            @SuppressLint("WebViewClientOnReceivedSslError")
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                android.util.Log.d("WebLogin", "page finished: " + url);
                captureToken(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                android.util.Log.d("WebLogin", "page start: " + url);
                // 阿里云的风控/处罚页，出现说明本次网页登录被安全策略拦下，token 不会下发。
                if (url != null && (url.contains("punish") || url.contains("ivCheckLogin") || url.contains("normal_validate"))) {
                    setHintText(getString(R.string.web_login_risk));
                }
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                android.util.Log.w("WebLogin", "resource error: " + request.getUrl() + " -> " + error.getDescription());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                android.util.Log.d("WebLogin", "console: " + message.message() + " @" + message.sourceId() + ":" + message.lineNumber());
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(WebLoginActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
        });

        // 卡片头部：标题 + 提示 + 关闭
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(12), dp(12), dp(8));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(R.string.web_login_title);
        title.setTextColor(onSurface);
        title.setTextSize(16);
        texts.addView(title);
        hintView = new TextView(this);
        hintView.setText(hintText());
        hintView.setTextColor(onSurfaceVariant);
        hintView.setTextSize(12);
        texts.addView(hintView);
        bar.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button close = new Button(this);
        close.setText(R.string.web_login_close);
        close.setOnClickListener(view -> close(false));
        bar.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 登录页成功后可能停留在 SSO 页面不跳转，这里给一个手动完成的出口。
        Button done = new Button(this);
        done.setText(R.string.web_login_done);
        done.setOnClickListener(view -> close(true));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneParams.setMarginEnd(dp(8));
        bar.addView(done, doneParams);
        card.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout pageContainer = new FrameLayout(this);
        GradientDrawable pageBackground = new GradientDrawable();
        pageBackground.setColor(Color.WHITE);
        pageBackground.setCornerRadius(dp(16));
        pageContainer.setBackground(pageBackground);
        pageContainer.setClipToOutline(true);
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        pageParams.setMargins(dp(12), 0, dp(12), dp(12));
        card.addView(pageContainer, pageParams);
        pageContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        onPageContainerReady(pageContainer);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = (int) (metrics.widthPixels * dialogWidthRatio());
        int height = (int) (metrics.heightPixels * dialogHeightRatio());
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(width, height, Gravity.CENTER);
        root.addView(card, cardParams);

        if (pageFocusable()) {
            webView.requestFocus();
        } else {
            webView.setFocusable(false);
            close.requestFocus();
        }
        return root;
    }

    private int color(int attr, int fallback) {
        try {
            return com.google.android.material.color.MaterialColors.getColor(
                    getWindow().getDecorView(), attr, fallback);
        } catch (Throwable e) {
            return fallback;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void startCheck() {
        handler.removeCallbacks(loginCheck);
        if (options != null && options.hasCookies()) handler.postDelayed(loginCheck, CHECK_INTERVAL);
    }

    private void checkLogin() {
        if (finished || isFinishing() || webView == null || options == null) return;
        if (options.isComplete(webView.getUrl(), url -> CookieManager.getInstance().getCookie(url))
                || options.isCompleteLoose(url -> CookieManager.getInstance().getCookie(url))) {
            close(true);
            return;
        }
        handler.postDelayed(loginCheck, CHECK_INTERVAL);
    }

    /**
     * @param success true 表示检测到登录完成，调用方应重试播放；false 表示用户主动退出。
     */
    private void close(boolean success) {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(loginCheck);
        saveCookies();
        try {
            CookieManager.getInstance().flush();
        } catch (Throwable ignored) {
        }
        AppWebLogin.INSTANCE.complete(success);
        finish();
    }

    /** 把登录页拿到的 cookie 落盘，供 spider（java.getCookie）与网盘请求复用。 */
    private void saveCookies() {
        if (options == null) return;
        CookieManager manager = CookieManager.getInstance();
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>(options.cookieUrls());
        // 登录过程中可能停在别的域名（SSO / 风控页），当前页面也一并保存
        if (webView != null && !TextUtils.isEmpty(webView.getUrl())) urls.add(webView.getUrl());
        for (String url : urls) {
            String cookie = manager.getCookie(url);
            if (TextUtils.isEmpty(cookie)) continue;
            CookieStore.put(url, cookie);
            // 同时写到父域，接口域名（drive-pc.quark.cn 等）也能命中
            CookieStore.putByHost(rootHost(CookieStore.hostOf(url)), cookie);
        }
    }

    /**
     * 阿里云盘的登录态在 localStorage 里（token + refresh_token），cookie 拿不到可用的 access_token。
     * 页面每次加载完成后抓一次，存在 CloudTokenStore 里供云盘账号模块刷新 token。
     */
    private void captureToken(String page) {
        if (webView == null || TextUtils.isEmpty(page) || !page.startsWith("http")) return;
        webView.evaluateJavascript(TOKEN_SCRIPT, value -> {
            String json = unquote(value);
            if (TextUtils.isEmpty(json) || !json.contains("refresh_token")) return;
            String host = CookieStore.hostOf(page);
            CloudTokenStore.putByHost(host, json);
            CloudTokenStore.putByHost(rootHost(host), json);
        });
    }

    /** pan.quark.cn → quark.cn；passport.alipan.com → alipan.com。 */
    private static String rootHost(String host) {
        if (TextUtils.isEmpty(host)) return "";
        String[] parts = host.split("\\.");
        if (parts.length < 3) return host;
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private static String unquote(String value) {
        if (TextUtils.isEmpty(value) || "null".equals(value)) return "";
        String text = value;
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text.replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "").replace("\\t", "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        startCheck();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(loginCheck);
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(loginCheck);
        if (!finished) {
            finished = true;
            saveCookies();
            AppWebLogin.INSTANCE.complete(false);
        }
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
