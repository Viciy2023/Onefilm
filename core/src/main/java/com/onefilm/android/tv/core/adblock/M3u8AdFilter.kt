package com.onefilm.android.tv.core.adblock

import java.net.URI
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * m3u8 广告分片过滤器（纯文本，无 Android 依赖）。
 *
 * 处理顺序：
 * 1. 非 m3u8 / 主播放列表 / 直播 → 原样返回；
 * 2. 命中配置规则（hosts + regex）→ 按规则删除；文本型规则删整段，URL 型规则删分片；
 * 3. 无规则命中 → 前缀聚类启发式兜底；
 * 4. 所有结果都过安全阀，异常或不达标一律回退原文。
 */
object M3u8AdFilter {

    data class Limits(
        /** 最多删除分片比例。 */
        val maxRemovedRatio: Double = 0.20,
        /** 最多删除时长（秒）。 */
        val maxRemovedSeconds: Double = 90.0,
        /** 处理后至少保留的分片数。 */
        val minRemainingSegments: Int = 3,
        /** 超过这个分片数不做启发式，避免 O(n²) 卡顿。 */
        val maxSegmentsForHeuristic: Int = 400,
    )

    data class Result(
        val text: String,
        val removedSegments: Int,
        val removedSeconds: Double,
        val rule: String? = null,
        val heuristic: Boolean = false,
    ) {
        val changed: Boolean get() = removedSegments > 0
    }

    fun filter(
        playlistUrl: String?,
        text: String,
        rules: List<AdBlockRule>,
        limits: Limits = Limits(),
    ): Result {
        if (text.isBlank() || !text.contains("#EXTM3U")) return unchanged(text)
        if (text.contains("#EXT-X-STREAM-INF")) return unchanged(text) // 主播放列表，交给子列表处理
        if (!text.contains("#EXT-X-ENDLIST")) return unchanged(text) // 直播不处理

        val newline = if (text.contains("\r\n")) "\r\n" else "\n"
        val normalized = text.replace("\r\n", "\n")

        val host = hostOf(playlistUrl)
        for (rule in rules) {
            if (!rule.matchesHost(host)) continue
            val applied = applyRule(normalized, rule) ?: continue
            val allowKeyRemoval = rule.textPatterns.any { it.pattern().contains("#EXT-X-KEY") }
            if (!isSafe(normalized, applied, limits, allowKeyRemoval)) continue
            return Result(applied.text.withNewline(newline), applied.removed, applied.seconds, rule.name, false)
        }

        val heuristic = applyHeuristic(normalized, playlistUrl, limits) ?: return unchanged(text)
        if (!isSafe(normalized, heuristic, limits)) return unchanged(text)
        return Result(heuristic.text.withNewline(newline), heuristic.removed, heuristic.seconds, null, true)
    }

    // ---------------------------------------------------------------- rule

    private fun applyRule(text: String, rule: AdBlockRule): Applied? {
        val textResult = applyTextRules(text, rule)
        if (textResult != null) return textResult
        return applyUrlRules(text, rule)
    }

    private fun applyTextRules(text: String, rule: AdBlockRule): Applied? {
        if (rule.textPatterns.isEmpty()) return null
        var out = text
        for (pattern in rule.textPatterns) {
            out = try {
                val matcher = pattern.matcher(out)
                if (!matcher.find()) out else matcher.replaceAll(replacementFor(pattern))
            } catch (t: Throwable) {
                return null
            }
        }
        if (out == text) return null
        return diff(text, out)
    }

    private fun replacementFor(pattern: Pattern): String {
        val raw = pattern.pattern().trimEnd()
        return if (raw.endsWith("#EXT-X-DISCONTINUITY")) {
            Matcher.quoteReplacement("#EXT-X-DISCONTINUITY")
        } else {
            ""
        }
    }

    private fun applyUrlRules(text: String, rule: AdBlockRule): Applied? {
        if (rule.urlPatterns.isEmpty()) return null
        val lines = text.split("\n")
        val segments = parseSegments(lines)
        if (segments.isEmpty()) return null
        val remove = mutableSetOf<Int>()
        for (segment in segments) {
            if (rule.urlPatterns.any { it.matcher(segment.url).find() }) {
                remove.add(segment.extinfIndex)
                remove.add(segment.urlIndex)
                val before = segment.extinfIndex - 1
                if (before >= 0 && lines[before].trim().startsWith("#EXT-X-DISCONTINUITY")) remove.add(before)
            }
        }
        if (remove.isEmpty()) return null
        val out = lines.filterIndexed { index, _ -> index !in remove }.joinToString("\n")
        return diff(text, out)
    }

    // ------------------------------------------------------------ heuristic

    private fun applyHeuristic(text: String, playlistUrl: String?, limits: Limits): Applied? {
        val lines = text.split("\n")
        val segments = parseSegments(lines)
        if (segments.size < 5 || segments.size > limits.maxSegmentsForHeuristic) return null

        val urls = segments.map { absolute(playlistUrl, it.url) }
        val maxLcp = IntArray(urls.size) { i ->
            var best = 0
            for (j in urls.indices) if (i != j) best = maxOf(best, lcp(urls[i], urls[j]))
            best
        }
        val groups = maxLcp.toList().groupingBy { it }.eachCount()
        val entry = groups.maxByOrNull { it.value } ?: return null
        if (entry.value < urls.size * 0.6) return null // 没有明显主簇，放弃
        val majority = urls.indices.filter { maxLcp[it] == entry.key }
        if (majority.size >= urls.size) return null

        val anchor = urls[majority.first()]
        val threshold = minOf(lcp(anchor, urls[majority.last()]), anchor.length - 1).coerceAtLeast(8)
        val remove = mutableSetOf<Int>()
        for (i in urls.indices) {
            if (i in majority) continue
            if (lcp(anchor, urls[i]) < threshold) {
                remove.add(segments[i].extinfIndex)
                remove.add(segments[i].urlIndex)
            }
        }
        if (remove.isEmpty()) return null
        val out = lines.filterIndexed { index, _ -> index !in remove }.joinToString("\n")
        return diff(text, out)
    }

    // ---------------------------------------------------------------- safety

    private fun isSafe(
        before: String,
        applied: Applied,
        limits: Limits,
        allowKeyRemoval: Boolean = false,
    ): Boolean {
        if (applied.removed <= 0) return false
        val beforeSegments = parseSegments(before.split("\n"))
        val afterSegments = parseSegments(applied.text.split("\n"))
        if (beforeSegments.isEmpty()) return false
        if (afterSegments.size < limits.minRemainingSegments) return false
        if (applied.removed > beforeSegments.size * limits.maxRemovedRatio) return false
        if (applied.seconds > limits.maxRemovedSeconds) return false
        if (!applied.text.contains("#EXTM3U") || !applied.text.contains("#EXT-X-ENDLIST")) return false
        if (!allowKeyRemoval && countTag(applied.text, "#EXT-X-KEY") < countTag(before, "#EXT-X-KEY")) return false
        if (!allowKeyRemoval && countTag(applied.text, "#EXT-X-MAP") < countTag(before, "#EXT-X-MAP")) return false
        return true
    }

    // ----------------------------------------------------------------- utils

    private data class Segment(
        val extinfIndex: Int,
        val urlIndex: Int,
        val duration: Double,
        val url: String,
    )

    private class Applied(val text: String, val removed: Int, val seconds: Double)

    private fun diff(before: String, after: String): Applied? {
        if (after == before) return null
        val beforeSegments = parseSegments(before.split("\n"))
        val afterSegments = parseSegments(after.split("\n"))
        val removed = beforeSegments.size - afterSegments.size
        if (removed <= 0) return null
        val seconds = beforeSegments.sumOf { it.duration } - afterSegments.sumOf { it.duration }
        return Applied(after, removed, maxOf(0.0, seconds))
    }

    private fun parseSegments(lines: List<String>): List<Segment> {
        val out = ArrayList<Segment>()
        var pendingIndex = -1
        var pendingDuration = 0.0
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF:")) {
                pendingIndex = i
                pendingDuration = parseDuration(line)
            } else if (!line.startsWith("#")) {
                if (pendingIndex >= 0) {
                    out.add(Segment(pendingIndex, i, pendingDuration, line))
                    pendingIndex = -1
                    pendingDuration = 0.0
                }
            }
        }
        return out
    }

    private val DURATION = Pattern.compile("#EXTINF:\\s*([0-9]+(?:\\.[0-9]+)?)")

    private fun parseDuration(line: String): Double {
        val matcher = DURATION.matcher(line)
        return if (matcher.find()) matcher.group(1)?.toDoubleOrNull() ?: 0.0 else 0.0
    }

    private fun countTag(text: String, tag: String): Int =
        text.split("\n").count { it.trim().startsWith(tag) }

    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            URI(url).host
        } catch (t: Throwable) {
            null
        }
    }

    private fun absolute(base: String?, segment: String): String {
        if (segment.startsWith("http://") || segment.startsWith("https://")) return segment
        if (base.isNullOrBlank()) return segment
        return try {
            URI(base).resolve(segment).toString()
        } catch (t: Throwable) {
            segment
        }
    }

    private fun lcp(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var i = 0
        while (i < limit && a[i] == b[i]) i++
        return i
    }

    private fun unchanged(text: String) = Result(text, 0, 0.0)

    private fun String.withNewline(newline: String): String =
        if (newline == "\n") this else replace("\n", newline)
}
