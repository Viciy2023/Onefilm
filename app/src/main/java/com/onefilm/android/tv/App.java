package com.onefilm.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.onefilm.android.tv.core.CoreContext;
import com.onefilm.android.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.google.gson.Gson;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private Activity activity;
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        long startupAt = android.os.SystemClock.uptimeMillis();
        installThreadGuard();
        CoreContext.INSTANCE.init(this);
        Notify.createChannel();
        registerActivityLifecycleCallbacks(this);
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setHomeDataSource(new com.onefilm.android.tv.composebridge.AppHomeDataSource());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setDetailDataSource(new com.onefilm.android.tv.composebridge.AppDetailDataSource());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setCategoryDataSource(new com.onefilm.android.tv.composebridge.AppCategoryDataSource());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setSearchDataSource(new com.onefilm.android.tv.composebridge.AppSearchDataSource());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setSearchMetaDataSource(new com.onefilm.android.tv.composebridge.AppSearchMetaDataSource());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setSourceDataSource(new com.onefilm.android.tv.composebridge.AppSourceDataSource());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setLibraryDataSource(new com.onefilm.android.tv.composebridge.AppLibraryDataSource());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setFavoriteStore(new com.onefilm.android.tv.composebridge.AppFavoriteStore());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setPlaybackResolver(new com.onefilm.android.tv.composebridge.AppPlaybackResolver());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setPlaybackHistoryStore(new com.onefilm.android.tv.composebridge.AppPlaybackHistoryStore());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setDanmakuDataSource(new com.onefilm.android.tv.composebridge.AppDanmakuDataSource());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setLegacyPlayerFactory(new com.onefilm.android.tv.composebridge.AppLegacyPlayerFactory());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setSettingsDataSource(new com.onefilm.android.tv.composebridge.AppSettingsDataSource());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setLiveLauncher(new com.onefilm.android.tv.composebridge.AppLiveLauncher());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setExternalIntentHandler(new com.onefilm.android.tv.composebridge.AppExternalIntentHandler());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setNetdiskDataSource(new com.onefilm.android.tv.composebridge.AppNetdiskDataSource());
        com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setUpdateDataSource(new com.onefilm.android.tv.composebridge.AppUpdateDataSource());
        try {
            Class<?> dlnaClass = Class.forName("com.onefilm.android.tv.composebridge.AppDlnaDataSource");
            com.onefilm.android.tv.compose.bridge.ComposeDependencies.INSTANCE.setDlnaDataSource((com.onefilm.android.tv.compose.bridge.DlnaDataSource) dlnaClass.getDeclaredConstructor().newInstance());
        } catch (Throwable ignored) {
        }

        Log.i("OneFilm", "application onCreate: " + (android.os.SystemClock.uptimeMillis() - startupAt) + "ms");
        startThreadMonitor();
    }

    private static final int THREAD_WARN_THRESHOLD = 300;
    /** 32 位盒子原生内存有限，超过该值就先留证据（实测崩溃前会出现 std::bad_alloc）。 */
    private static final long MEMORY_WARN_BYTES = 512L * 1024 * 1024;

    /**
     * 每分钟采样一次线程数与内存。盒子上线程/原生内存耗尽会直接 SIGABRT，
     * 这里在接近上限时留记录并清一次图片内存缓存；不动 spider/WebView
     * （归属不明确，误清理会打断播放）。
     */
    private void startThreadMonitor() {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    int count = Thread.activeCount();
                    long nativeHeap = android.os.Debug.getNativeHeapAllocatedSize();
                    long javaHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    long used = nativeHeap + javaHeap;
                    if (count >= THREAD_WARN_THRESHOLD || used >= MEMORY_WARN_BYTES) {
                        Log.w("OneFilm", "resource warning: threads=" + count
                                + " native=" + (nativeHeap / 1048576) + "MB"
                                + " java=" + (javaHeap / 1048576) + "MB");
                        com.onefilm.android.tv.utils.CrashLog.note(
                                App.this,
                                "threads=" + count + " native=" + (nativeHeap / 1048576)
                                        + "MB java=" + (javaHeap / 1048576) + "MB");
                        com.bumptech.glide.Glide.get(App.this).clearMemory();
                    }
                } catch (Throwable ignored) {
                }
                handler.postDelayed(this, 60_000L);
            }
        }, 60_000L);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    /**
     * 盒子上的线程数有上限，第三方库（本地代理、网盘、goproxy 等）在请求高峰
     * 会不断 new Thread；一旦到顶，Thread.start() 抛
     * OutOfMemoryError: pthread_create failed，未捕获就会把整个进程带走。
     * 这里只兜住这一种 OOM（主线程除外），其余异常仍交给原有崩溃处理。
     */
    private void installThreadGuard() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // 先落盘，保证任何未捕获异常都有现场（包括线程创建失败）。
            com.onefilm.android.tv.utils.CrashLog.write(this, thread, throwable);
            boolean threadCreationFailed = throwable instanceof OutOfMemoryError
                    && throwable.getMessage() != null
                    && throwable.getMessage().contains("pthread_create");
            if (threadCreationFailed && !"main".equals(thread.getName())) {
                Log.e("OneFilm", "thread creation failed on " + thread.getName(), throwable);
                return;
            }
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }
}