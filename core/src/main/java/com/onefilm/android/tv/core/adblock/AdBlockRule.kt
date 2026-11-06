package com.onefilm.android.tv.core.adblock

import java.util.regex.Pattern

/**
 * 配置里的一条广告过滤规则（对应 config JSON 的 rules[]）。
 *
 * [regexes] 有两种形态：
 * - 含 `#EXT`：直接作用于 m3u8 文本（整段删除广告块）；
 * - 不含 `#EXT`：按分片 URL 匹配（如 `15.1666`），删除命中的分片。
 */
class AdBlockRule(
    val name: String,
    val hosts: List<String>,
    regexes: List<String>,
) {

    val patterns: List<Pattern> = regexes
        .filter { it.isNotBlank() }
        .map { Pattern.compile(it) }

    val textPatterns: List<Pattern> = patterns.filter { it.pattern().contains("#EXT") }
    val urlPatterns: List<Pattern> = patterns.filterNot { it.pattern().contains("#EXT") }

    fun matchesHost(urlHost: String?): Boolean {
        if (urlHost.isNullOrEmpty() || hosts.isEmpty()) return false
        val host = urlHost.lowercase()
        return hosts.any { it.isNotBlank() && host.contains(it.lowercase()) }
    }

    override fun toString(): String = "AdBlockRule($name)"
}
