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
 * 4. 仍无结果且提供了 [PtsProbe] → PTS 连续性检测兜底；
 * 5. 所有结果都过安全阀，异常或不达标一律回退原文。
 *
 * 前两级命中即返回，PTS 阶段不会产生任何额外网络请求。
 */
object M3u8AdFilter {

    /** 锚点聚类时，LCP 至少达到该长度才算「同簇」。 */
    private const val MIN_ANCHOR_LCP = 16

    /** 分组兜底：中插广告组的最短/最长时长（毫秒）。 */
    private const val MIN_BREAK_MILLIS = 4_000L
    private const val MAX_BREAK_MILLIS = 120_000L

    /** 分组兜底：中插广告组的最多分片数。 */
    private const val MAX_BREAK_SEGMENTS = 40

    /** 分组兜底：广告组左右相邻组至少要有的时长（毫秒）。 */
    private const val MIN_NEIGHBOR_MILLIS = 180_000L

    /**
     * PTS 探测钩子（由 App 层实现网络与预算控制）。
     *
     * 入参为每组首分片的绝对 URL，返回值必须与入参等长，探测失败的位置为 null。
     * 实现不得抛出异常；超预算时返回已拿到的结果即可。
     */
    fun interface PtsProbe {
        fun probe(groupFirstSegmentUrls: List<String>): List<Long?>
    }

    data class Limits(
        /** 最多删除分片比例。 */
        val maxRemovedRatio: Double = 0.20,
        /** 最多删除时长（秒）。 */
        val maxRemovedSeconds: Double = 90.0,
        /** 处理后至少保留的分片数。 */
        val minRemainingSegments: Int = 3,
        /** 超过这个分片数不做启发式。锚点聚类为 O(n)，长剧集也能覆盖。 */
        val maxSegmentsForHeuristic: Int = 3000,
        /** PTS 阶段最多探测的组数（组数在此范围内则全部探测）。 */
        val maxPtsGroups: Int = 64,
    )

    data class Result(
        val text: String,
        val removedSegments: Int,
        val removedSeconds: Double,
        val rule: String? = null,
        val heuristic: Boolean = false,
        val pts: Boolean = false,
    ) {
        val changed: Boolean get() = removedSegments > 0
    }

    @JvmOverloads
    fun filter(
        playlistUrl: String?,
        text: String,
        rules: List<AdBlockRule>,
        limits: Limits = Limits(),
        probe: PtsProbe? = null,
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

        val heuristic = applyHeuristic(normalized, playlistUrl, limits)
        if (heuristic != null && isSafe(normalized, heuristic, limits)) {
            return Result(heuristic.text.withNewline(newline), heuristic.removed, heuristic.seconds, null, true)
        }

        val pts = applyPts(normalized, playlistUrl, limits, probe)
        if (pts != null && isSafe(normalized, pts, limits)) {
            return Result(pts.text.withNewline(newline), pts.removed, pts.seconds, null, false, true)
        }

        // 末级兜底：加密 / fMP4 / PTS 探测无证据时，用 discontinuity 分组时长判断中插广告。
        val groupBreak = applyGroupBreak(normalized, playlistUrl, limits)
        if (groupBreak != null && isSafe(normalized, groupBreak, limits)) {
            return Result(groupBreak.text.withNewline(newline), groupBreak.removed, groupBreak.seconds, null, true)
        }
        return unchanged(text)
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
        // 锚点支持度聚类：从首/中/尾取候选锚点，选「同簇分片最多」的锚点，
        // 再按簇内 LCP 中位数作为阈值剔除异簇分片。整体 O(n)，可覆盖上千分片的长剧集。
        val candidates = listOf(urls.first(), urls[urls.size / 2], urls.last()).distinct()
        var anchor = candidates.first()
        var support = emptyList<Int>()
        for (candidate in candidates) {
            val members = urls.indices.filter { lcp(urls[it], candidate) >= MIN_ANCHOR_LCP }
            if (members.size > support.size) {
                anchor = candidate
                support = members
            }
        }
        if (support.size < urls.size * 0.6) return null // 没有明显主簇，放弃
        if (support.size >= urls.size) return null

        val anchorLcps = support.map { lcp(urls[it], anchor) }.sorted()
        val threshold = anchorLcps[anchorLcps.size / 2]
            .coerceAtLeast(MIN_ANCHOR_LCP)
            .coerceAtMost(anchor.length - 1)
        val remove = mutableSetOf<Int>()
        for (i in urls.indices) {
            if (i in support) continue
            if (lcp(anchor, urls[i]) < threshold) {
                remove.add(segments[i].extinfIndex)
                remove.add(segments[i].urlIndex)
            }
        }
        if (remove.isEmpty()) return null
        val out = lines.filterIndexed { index, _ -> index !in remove }.joinToString("\n")
        return diff(text, out)
    }

    // ------------------------------------------------------------------ pts

    /**
     * 第三阶段：PTS 连续性兜底。
     *
     * 只有点播、非加密、非 BYTERANGE、分组数在 [Limits.maxPtsGroups] 内才会探测。
     * 无证据（探测成功少于 2 组）或分类为空时返回 null，调用方回退原文。
     */
    private fun applyPts(
        text: String,
        playlistUrl: String?,
        limits: Limits,
        probe: PtsProbe?,
    ): Applied? {
        if (probe == null) return null
        if (text.contains("#EXT-X-KEY")) return null // 加密分片无法解析 PES
        if (text.contains("#EXT-X-BYTERANGE")) return null
        if (text.contains("#EXT-X-MAP")) return null // fMP4 不含 TS PES

        val lines = text.split("\n")
        val groups = parseGroups(lines, playlistUrl)
        if (groups.size < 2) return null
        if (groups.any { it.segmentCount <= 0 }) return null

        // 组数在上限内则全部探测；超过上限时等距抽样，保证覆盖整条时间轴，
        // 避免像之前那样只探测前 N 组而漏掉 6 分钟处的插播。
        val limited: List<Int> = if (groups.size <= limits.maxPtsGroups) {
            groups.indices.toList()
        } else {
            val budget = limits.maxPtsGroups
            val step = kotlin.math.ceil(groups.size.toDouble() / budget).toInt().coerceAtLeast(1)
            val picked = LinkedHashSet<Int>()
            var index = 0
            while (index < groups.size && picked.size < budget) {
                picked.add(index)
                index += step
            }
            picked.add(groups.size - 1)
            picked.take(budget).sorted()
        }
        // 探测顺序：先用等距铺开的 16 个组覆盖整条时间轴（并发首波就能覆盖到 6 分钟这类位置），
        // 其余组随后补测。避免像之前那样首波全是前 24 组、广告组恰好超时的情况。
        val positions = limited.indices.toList()
        val wave = minOf(16, positions.size)
        val spread = if (wave > 0) (0 until wave).map { positions[(it * positions.size) / wave] } else emptyList()
        val targets: List<Int> = (spread + positions).distinct().map { limited[it] }

        val ptsValues = try {
            probe.probe(targets.map { groups[it].firstSegmentUrl })
        } catch (t: Throwable) {
            return null
        }
        if (ptsValues.size != targets.size) return null

        val probed = groups.mapIndexed { index, group ->
            val position = targets.indexOf(index)
            group.copy(ptsMillis = if (position >= 0) ptsValues[position] else null)
        }
        if (probed.count { it.ptsMillis != null } < 2) return null

        val verdict = HlsPtsContinuity.classify(probed) ?: return null
        if (verdict.adGroups.isEmpty()) return null
        if (HlsPtsContinuity.longestAdBreakMillis(probed, verdict.adGroups) >
            HlsPtsContinuity.MAX_AD_BREAK_MILLIS
        ) {
            return null
        }

        val remove = HashSet<Int>()
        for (index in verdict.adGroups) {
            for (line in groups[index].startLine..groups[index].endLine) remove.add(line)
        }
        if (remove.isEmpty()) return null
        val out = lines.filterIndexed { index, _ -> index !in remove }.joinToString("\n")
        return diff(text, out)
    }

    /**
     * 末级兜底：按 `#EXT-X-DISCONTINUITY` 分组，中间出现「短组」（4s–120s、分片 ≤ 40）
     * 且左右相邻组都很长（≥180s）时，视为中插广告整组删除。
     *
     * 用于加密（`#EXT-X-KEY`）、fMP4（`#EXT-X-MAP`）、BYTERANGE 等无法做 PTS 探测的播放列表。
     */
    private fun applyGroupBreak(text: String, playlistUrl: String?, limits: Limits): Applied? {
        val lines = text.split("\n")
        val groups = parseGroups(lines, playlistUrl)
        if (groups.size < 3) return null

        val remove = LinkedHashSet<Int>()
        var removedMs = 0L
        for (i in 1 until groups.size - 1) {
            val group = groups[i]
            if (group.durationMillis < MIN_BREAK_MILLIS) continue
            if (group.durationMillis > MAX_BREAK_MILLIS) continue
            if (group.segmentCount > MAX_BREAK_SEGMENTS) continue
            val prev = groups[i - 1].durationMillis
            val next = groups[i + 1].durationMillis
            if (prev < MIN_NEIGHBOR_MILLIS || next < MIN_NEIGHBOR_MILLIS) continue
            remove.add(i)
            removedMs += group.durationMillis
        }
        if (remove.isEmpty()) return null
        if (removedMs > (limits.maxRemovedSeconds * 1000).toLong()) return null

        val drop = HashSet<Int>()
        for (index in remove) {
            for (line in groups[index].startLine..groups[index].endLine) drop.add(line)
        }
        if (drop.isEmpty()) return null
        val out = lines.filterIndexed { index, _ -> index !in drop }.joinToString("\n")
        return diff(text, out)
    }

    /** 按 `#EXT-X-DISCONTINUITY` 切组，记录每组首片的行号与绝对 URL。 */
    private fun parseGroups(lines: List<String>, playlistUrl: String?): List<HlsPtsContinuity.Group> {
        val out = ArrayList<HlsPtsContinuity.Group>()

        var startLine = -1
        var endLine = -1
        var firstUrl: String? = null
        var durationMillis = 0L
        var segmentCount = 0
        var pendingIndex = -1
        var pendingDuration = 0.0

        fun flush() {
            val url = firstUrl
            if (segmentCount > 0 && startLine >= 0 && endLine >= startLine && url != null) {
                out.add(
                    HlsPtsContinuity.Group(
                        index = out.size,
                        startLine = startLine,
                        endLine = endLine,
                        durationMillis = durationMillis,
                        segmentCount = segmentCount,
                        firstSegmentUrl = absolute(playlistUrl, url),
                    ),
                )
            }
            startLine = -1
            endLine = -1
            firstUrl = null
            durationMillis = 0L
            segmentCount = 0
        }

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXT-X-DISCONTINUITY")) {
                flush()
                continue
            }
            if (line.startsWith("#EXTINF:")) {
                pendingIndex = i
                pendingDuration = parseDuration(line)
                continue
            }
            if (!line.startsWith("#") && pendingIndex >= 0) {
                if (segmentCount == 0) {
                    startLine = pendingIndex
                    firstUrl = line
                }
                endLine = i
                durationMillis += (pendingDuration * 1000.0).toLong()
                segmentCount++
                pendingIndex = -1
                pendingDuration = 0.0
            }
        }
        flush()
        return out
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
