package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.bean.Site

/**
 * 搜索源的调度策略。
 *
 * <p>有一类源（网盘 / GoProxy 系，接口形如 {@code csp_Wex*Guard}、{@code csp_*Pan*}）
 * 在首次使用时会下载并加载一个约 10MB 的原生代理库（{@code libwexproxy.so}），
 * 还会 fork 出辅助进程。在 2GB 的电视上并发加载会让进程直接 SIGSEGV，
 * 崩溃点就在那个 .so 里，App 层无法捕获。
 *
 * <p>所以这类源必须：冷启动时单独串行初始化、失败即跳过、并发恒为 1。
 */
object SearchSitePolicy {

    /** 网盘 / GoProxy 系源的接口特征。 */
    private val NATIVE_HEAVY = Regex("(?i).*(wex|pan).*")

    /** 是否为需要单独串行初始化的原生重源。 */
    fun isNativeHeavy(site: Site): Boolean {
        val api = site.api ?: return false
        return api.isNotEmpty() && NATIVE_HEAVY.matches(api)
    }
}
