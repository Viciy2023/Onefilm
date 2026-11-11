package com.onefilm.android.tv.core.adblock

import kotlin.math.abs

/**
 * HLS 分片时间轴连续性判定（纯计算，无网络、无 Android 依赖）。
 *
 * 输入是按 `#EXT-X-DISCONTINUITY` 切开的分组（每组已带上首片 PTS），
 * 输出哪些组属于独立时间轴的广告插播。
 *
 * 思路：
 * 1. 以每组首片 PTS + 组时长作为游标，从每个已探测组向前、向后建链；
 * 2. 覆盖时长最大的链视为正片时间轴；
 * 3. 链外且 PTS 从 0 附近起算（独立转码）的组判为广告；
 * 4. 紧接已判广告组的组，若与上一组时间轴连续，也判为同一段插播。
 *
 * 探测失败的组（[Group.ptsMillis] 为 null）不参与游标计算，但会随相邻链成员保留。
 */
object HlsPtsContinuity {

    /** PTS 与预期时间轴的允许误差。 */
    const val TOLERANCE_MILLIS = 2_000L

    /** 独立转码的广告通常从 0 附近起算，视为“新时间轴”的 PTS 上限。 */
    const val FRESH_TIMELINE_MILLIS = 10_000L

    /** 单段插播的最大时长，超过则不删。 */
    const val MAX_AD_BREAK_MILLIS = 60_000L

    /**
     * 一个 discontinuity 分组。
     *
     * [startLine] 为组内首个 `#EXTINF` 的行号，[endLine] 为组内最后一个分片 URL 的行号（含）。
     */
    data class Group(
        val index: Int,
        val startLine: Int,
        val endLine: Int,
        val durationMillis: Long,
        val segmentCount: Int,
        val firstSegmentUrl: String,
        val ptsMillis: Long? = null,
    )

    data class Verdict(
        /** 判为广告的组下标。 */
        val adGroups: Set<Int>,
        /** 正片时间轴上的组下标。 */
        val mainGroups: Set<Int>,
    )

    fun classify(groups: List<Group>): Verdict? {
        if (groups.size < 2) return null
        if (groups.count { it.ptsMillis != null } < 2) return null

        var best: Set<Int>? = null
        var bestCovered = -1L

        for (start in groups.indices) {
            val startPts = groups[start].ptsMillis ?: continue
            val members = LinkedHashSet<Int>()
            members.add(start)
            var covered = groups[start].durationMillis

            var cursor = startPts + groups[start].durationMillis
            for (i in start + 1 until groups.size) {
                val pts = groups[i].ptsMillis
                if (pts == null) {
                    members.add(i)
                    covered += groups[i].durationMillis
                    continue
                }
                if (pts >= cursor - TOLERANCE_MILLIS && pts <= cursor + TOLERANCE_MILLIS) {
                    members.add(i)
                    covered += groups[i].durationMillis
                    cursor = pts + groups[i].durationMillis
                }
            }

            var back = startPts
            for (i in start - 1 downTo 0) {
                val pts = groups[i].ptsMillis
                if (pts == null) {
                    members.add(i)
                    covered += groups[i].durationMillis
                    continue
                }
                val end = pts + groups[i].durationMillis
                if (end >= back - TOLERANCE_MILLIS && end <= back + TOLERANCE_MILLIS) {
                    members.add(i)
                    covered += groups[i].durationMillis
                    back = pts
                }
            }

            if (covered > bestCovered) {
                bestCovered = covered
                best = members
            }
        }

        val main = best ?: return null
        val ads = LinkedHashSet<Int>()

        for (i in groups.indices) {
            val pts = groups[i].ptsMillis ?: continue
            if (i in main) continue
            if (i == 0) continue // 首组位置特殊，宁可保留

            var independent = pts <= FRESH_TIMELINE_MILLIS
            if (!independent) {
                val prev = groups[i - 1]
                val prevPts = prev.ptsMillis
                if (prevPts != null && (i - 1) in ads) {
                    val expected = prevPts + prev.durationMillis
                    independent = abs(pts - expected) <= TOLERANCE_MILLIS
                }
            }
            if (independent) ads.add(i)
        }

        // 探测预算有限时，广告段里可能有组没探测到（PTS 为 null）；
        // 与已判定为广告的组相邻的未探测组，按同一段广告处理。
        var expanded = true
        while (expanded) {
            expanded = false
            for (i in 1 until groups.size) {
                if (i in ads || groups[i].ptsMillis != null) continue
                if ((i - 1) in ads || (i + 1) in ads) {
                    // 只有后面还有探测到的组时才扩展，避免早停后把尾部整段未探测内容都当广告。
                    val hasProbedAfter = (i + 1 until groups.size).any { groups[it].ptsMillis != null }
                    if (hasProbedAfter) {
                        ads.add(i)
                        expanded = true
                    }
                }
            }
        }

        return Verdict(ads, main)
    }

    /** 相邻广告组视为同一段插播，返回最长一段的毫秒数。 */
    fun longestAdBreakMillis(groups: List<Group>, ads: Set<Int>): Long {
        var longest = 0L
        var run = 0L
        var previous = -2
        for (index in ads.sorted()) {
            run = if (index == previous + 1) {
                run + groups[index].durationMillis
            } else {
                groups[index].durationMillis
            }
            if (run > longest) longest = run
            previous = index
        }
        return longest
    }
}
