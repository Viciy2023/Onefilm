package com.onefilm.android.tv.lite

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import com.onefilm.android.tv.App
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * LITE 的 Application。
 *
 * 低密度小屏（典型是 1024x600 @130dpi 的投影仪）上，系统密度只有 0.8125，
 * 相当于把 960dp 设计稿排到 1260dp 宽，所有 dp/sp 元素都会缩到 0.76 倍。
 *
 * TV 版的 View 界面由 `BaseActivity.autoConvertDensityOfGlobal()` 处理；
 * Compose 界面不继承 BaseActivity，所以这里在 Activity 创建时把密度对齐到
 * 「屏宽 = 960dp」。
 *
 * 注意两点（都是真机踩出来的）：
 * 1. 必须改 Activity 的 Resources，改 Application 的 base context 不生效；
 * 2. 必须同时重算 `Configuration.screenWidthDp / screenHeightDp`，否则
 *    Compose Dialog 会按「旧 dp × 新密度」得到 1347px 的超宽窗口（设置面板被切掉一半）。
 *
 * 只在 densityDpi < 160（低于 mdpi）时生效，手机/平板不受影响。
 */
class LiteApp : App() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyDesignDensity(activity)
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    @Suppress("DEPRECATION")
    private fun applyDesignDensity(activity: Activity) {
        try {
            val res = activity.resources
            val dm = res.displayMetrics
            if (dm.densityDpi >= 160) return

            val widthPx = max(dm.widthPixels, dm.heightPixels)
            val heightPx = min(dm.widthPixels, dm.heightPixels)
            if (widthPx <= 0 || heightPx <= 0) return

            val targetDpi = (widthPx * 160f / DESIGN_WIDTH_DP).roundToInt()
            if (targetDpi <= dm.densityDpi) return
            val targetDensity = targetDpi / 160f

            val oldConfig = res.configuration
            val config = Configuration(oldConfig)
            config.densityDpi = targetDpi
            config.screenWidthDp = (widthPx / targetDensity).roundToInt()
            config.screenHeightDp = (heightPx / targetDensity).roundToInt()
            config.smallestScreenWidthDp = min(config.screenWidthDp, config.screenHeightDp)

            val metrics = DisplayMetrics()
            metrics.setTo(dm)
            metrics.density = targetDensity
            metrics.densityDpi = targetDpi
            metrics.scaledDensity = targetDensity * oldConfig.fontScale

            res.updateConfiguration(config, metrics)
            android.util.Log.i(
                "LiteDensity",
                "applied ${activity.javaClass.simpleName} dpi=$targetDpi density=$targetDensity " +
                        "dp=${config.screenWidthDp}x${config.screenHeightDp}",
            )
        } catch (t: Throwable) {
            // 密度调整失败不能影响启动
        }
    }

    private companion object {
        /** 与 leanback 资源里的 design_width_in_dp 保持一致 */
        const val DESIGN_WIDTH_DP = 960f
    }
}
