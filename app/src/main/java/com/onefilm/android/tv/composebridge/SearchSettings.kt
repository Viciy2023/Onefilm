package com.onefilm.android.tv.composebridge

import com.github.catvod.utils.Crypto
import com.github.catvod.utils.Prefers
import com.onefilm.android.tv.compose.bridge.SEARCH_THREAD_OPTIONS

/**
 * 搜索相关的用户设置。
 *
 * - 搜索线程数：12/16/24/36/48，默认 16；
 * - 参与搜索的站点：按“被排除的站点 key”保存，默认全部参与，
 *   换接口后自动回到全选（新接口不会继承旧接口的排除项）。
 */
object SearchSettings {

    const val DEFAULT_THREADS = 16

    val THREAD_OPTIONS = SEARCH_THREAD_OPTIONS

    private const val KEY_THREADS = "search_threads"
    private const val KEY_DISABLED = "search_sites_disabled_"

    fun threads(): Int = normalizeThreads(Prefers.getInt(KEY_THREADS, DEFAULT_THREADS))

    fun putThreads(value: Int) {
        Prefers.put(KEY_THREADS, normalizeThreads(value))
    }

    fun normalizeThreads(value: Int): Int =
        if (THREAD_OPTIONS.contains(value)) value else DEFAULT_THREADS

    fun disabledSites(configUrl: String): Set<String> =
        decodeDisabled(Prefers.getString(keyDisabled(configUrl), ""))

    fun setSiteEnabled(configUrl: String, siteKey: String, enabled: Boolean) {
        if (siteKey.isBlank()) return
        val next = disabledSites(configUrl).toMutableSet()
        if (enabled) next.remove(siteKey) else next.add(siteKey)
        Prefers.put(keyDisabled(configUrl), encodeDisabled(next))
    }

    fun setAllEnabled(configUrl: String, siteKeys: Collection<String>, enabled: Boolean) {
        val next = if (enabled) emptySet() else siteKeys.filter { it.isNotBlank() }.toSet()
        Prefers.put(keyDisabled(configUrl), encodeDisabled(next))
    }

    /** 站点勾选变化后用于隔离搜索缓存，避免命中旧站点集合的结果。 */
    fun scopeSignature(configUrl: String): String {
        val disabled = disabledSites(configUrl).sorted().joinToString(",")
        return Crypto.md5(configUrl.trim() + "|" + disabled)
    }

    fun encodeDisabled(keys: Set<String>): String =
        keys.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .joinToString("\n")

    fun decodeDisabled(raw: String): Set<String> =
        raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun keyDisabled(configUrl: String): String {
        val scope = configUrl.trim().ifEmpty { "default" }
        return KEY_DISABLED + Crypto.md5(scope)
    }
}
