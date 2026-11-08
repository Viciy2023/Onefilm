package com.onefilm.android.tv.core.danmaku

/**
 * 弹幕搜索的标题/集名规范化（纯逻辑，TV 与手机共用）。
 *
 * 服务器（danmu_api）是模糊关键词搜索：
 * - name 里的年份/清晰度会被它清洗，但“黄日华版/周星驰版/1994版本”不会，会污染搜索；
 * - episode 必须能提取出数字，`HD国语`/`国语中字`/`HD粤语` 这类标签没有数字，会选错集；
 * - 电影线路名常是 `1080`/`2160`，不能当集号。
 */
object DanmakuQuery {

    private val YEAR = Regex("(19|20)\\d{2}")
    private val YEAR_BRACKET = Regex("[\\(（\\[【]\\s*(19|20)\\d{2}\\s*[\\)）\\]】]")
    private val LANGUAGE = Regex(
        "[\\(（\\[【]\\s*(粤语|国语|普通话|英语|中字|中文字幕|双语)\\s*[\\)）\\]】]|(?:粤语|国语|普通话|英语|中字|中文字幕|双语)$",
    )
    private val QUALITY = Regex(
        "[\\(（\\[【]?\\s*(2160p|1080p|720p|4k|hd|高清|蓝光|枪版|抢先版|tc|ts|web-?dl|bluray|hdr|dv|x265|x264|60fps)\\s*[\\)）\\]】]?",
        RegexOption.IGNORE_CASE,
    )
    private val VERSION_NUM = Regex("\\d{2,4}\\s*版(?:本)?\\s*$")
    private val VERSION_KNOWN = Regex(
        "(电影版|剧场版|完整版|未删减版|重制版|修复版|加长版|导演剪辑版|国配版|粤配版|台配版|国语版|粤语版|普通话版|高清版|蓝光版)\\s*$",
    )
    private val VERSION_SPACED = Regex(
        "[\\s\\-_/（(\\[【]+[\\u4e00-\\u9fa5A-Za-z0-9]{1,6}版(?:本)?[)）\\]】]?\\s*$",
    )
    private val TRAILING = Regex("[\\s\\-_/|*·]+$")
    private val TAIL_PLATFORM = Regex("【([^】]+)】")
    private val ORIGIN = Regex("from\\s+([A-Za-z0-9_]+)", RegexOption.IGNORE_CASE)
    private val CN_EPISODE = Regex("第\\s*(\\d+)\\s*[集话期回]")

    private val EPISODE_PATTERNS = listOf(
        Regex("第\\s*(\\d{1,4})\\s*[集话期回]"),
        Regex("(?i)s\\d{1,2}\\s*e(\\d{1,4})"),
        Regex("(?i)(?:^|[^a-z])ep?\\s*(\\d{1,4})"),
        Regex("(\\d{1,4})\\s*[集话]"),
    )
    private val MOVIE_LABEL = Regex(
        "(?i)\\b(4k|8k|2160p?|1080p?|720p?|576p?|480p?|uhd|fhd|hd|hdr|blu-?ray|web-?dl|remux|hevc|x265|x264|h265|h264|60fps|tc|ts|cam)\\b" +
            "|蓝光|高清|超清|原画|正片|预告|花絮|完整版|国语|粤语|中字|中文字幕|双语|普通话|国配|粤配|修复版|重制版|抢先|枪版|电影版|剧场版",
    )
    private val PLAIN_NUMBER = Regex("^\\s*(\\d{1,4})\\s*$")
    private val RESOLUTIONS = setOf(480, 576, 720, 1080, 2160, 4320)

    /** 基础片名：去掉年份、语言、清晰度、演员/版本后缀。 */
    fun bestName(name: String): String {
        var text = name.trim()
        text = text.replace(YEAR_BRACKET, " ")
        text = text.replace(LANGUAGE, " ")
        text = text.replace(QUALITY, " ")
        text = text.trim()
        text = stripVersion(text)
        text = text.replace(YEAR, " ")
        text = text.replace(TRAILING, "").trim()
        return if (text.length >= 2) text else name.trim()
    }

    /**
     * 去掉尾部的版本标记：
     * - `97版`/`1994版本` 这类数字版本号，直接去掉；
     * - `电影版`/`剧场版`/`未删减版` 等已知后缀，直接去掉；
     * - ` 黄日华版` 这类前面有分隔符的演员版，去掉；
     * 纯 `黄日华版`（无分隔符）不做猜测，避免把整个片名吃掉。
     */
    private fun stripVersion(text: String): String {
        var result = text.replace(VERSION_NUM, " ")
        result = result.replace(VERSION_KNOWN, " ")
        result = result.replace(VERSION_SPACED, " ")
        return result
    }

    /** 从标题里提取 4 位年份。 */
    fun yearOf(name: String): String? = YEAR.find(name)?.value

    /**
     * 集名规范化：
     * - 只认 `第1集` / `S01E05` / `EP05` / `1集` / 纯数字 `01`；
     * - 电影线路名 `1080`/`2160P`/`4K`/`HD`/`蓝光`/`正片`/`HD国语`/`HD粤语`/`国语`/`粤语` 等一律按电影处理（1）；
     * - 其余带数字但不像集号的（`2026`、`60帧`）回退到当前集序号。
     */
    fun bestEpisode(episode: String, fallbackNumber: Int? = null): String {
        val text = episode.trim()
        val fallback = (fallbackNumber?.takeIf { it in 1..9999 } ?: 1).toString()
        if (text.isEmpty()) return fallback
        for (pattern in EPISODE_PATTERNS) {
            val number = pattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
            if (number != null && number in 1..9999) return number.toString()
        }
        if (MOVIE_LABEL.containsMatchIn(text)) return fallback
        val plain = PLAIN_NUMBER.find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (plain != null && plain in 1..999 && plain !in RESOLUTIONS) return plain.toString()
        return fallback
    }

    /** 从服务器返回的源名里提取年份。 */
    fun sourceYear(sourceName: String): String? =
        YEAR.find(sourceName.substringBefore(" - "))?.value

    /** 从服务器返回的源名里提取“第N集”的集号，没有集号返回 null。 */
    fun episodeOf(sourceName: String): Int? =
        CN_EPISODE.find(sourceName)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * 实际弹幕平台在 ` - ` 之后的第一个【】里：
     * `功夫(2004)【电影】from 360 - 【bilibili1】 功夫(2004)` → `bilibili1`
     */
    fun platformOf(sourceName: String): String {
        val tail = sourceName.substringAfter(" - ", "")
        return TAIL_PLATFORM.find(tail)?.groupValues?.get(1)?.lowercase().orEmpty()
    }

    /**
     * 平台抓取速度分（只用于同分排序，远小于年份 1000 / 片名 500）。
     * 实测：bilibili 6~8s，360→bilibili1 约 9s，360→imgo 约 19s，
     * youku 11~13s，iqiyi 26~72s，tencent 52s，migu 95s+。
     */
    private fun platformScore(platform: String): Int = when {
        platform.contains("bilibili") -> 70
        platform == "360" -> 60
        platform.contains("imgo") -> 55
        platform.contains("youku") -> 50
        platform.contains("qiyi") -> 20
        platform.contains("qq") || platform.contains("tencent") -> 10
        platform.contains("sohu") || platform.contains("xigua") -> 5
        platform.contains("migu") -> -100
        else -> 0
    }

    /**
     * 平台身份（用于“每平台只取 1 条”）。
     * `from iqiyi - 【qiyi】` → qiyi；`from 360 - 【bilibili1】` → 360-bilibili（与纯 bilibili 区分）。
     */
    fun platformKey(sourceName: String): String {
        val origin = ORIGIN.find(sourceName.substringBefore(" - "))
            ?.groupValues?.get(1)?.lowercase().orEmpty()
        val provider = platformOf(sourceName)
        return when {
            provider.contains("bilibili") && origin == "360" -> "360-bilibili"
            provider.isNotBlank() -> provider
            origin.isNotBlank() -> origin
            else -> sourceName
        }
    }

    /**
     * 平台优先级：bilibili > 360(bilibili1) > imgo > youku > iqiyi > tencent > migu > 其他。
     * 只用于选源顺序，不参与“是否匹配”的判断。
     */
    fun platformRank(sourceName: String): Int {
        val key = platformKey(sourceName)
        return when {
            key == "360-bilibili" -> 1
            key.contains("bilibili") -> 0
            key == "360" -> 1
            key.contains("imgo") -> 2
            key.contains("youku") -> 3
            key.contains("qiyi") -> 4
            key.contains("qq") || key.contains("tencent") -> 5
            key.contains("migu") -> 6
            else -> 9
        }
    }

    /**
     * 基础分：年份 + 片名 + 集数，用来判断“是不是这部片的这一集”，不含平台偏好。
     * 片名完全匹配 500 / 包含 100，年份匹配 1000，电影 10，集数匹配 ±200/300。
     */
    fun baseScore(sourceName: String, year: String, baseName: String, episode: String = ""): Int {
        val anime = sourceName.substringBefore(" - ").trim()
        val title = anime
            .substringBefore("(")
            .substringBefore("（")
            .substringBefore("[")
            .substringBefore("【")
            .trim()
        var score = 0
        if (title == baseName) score += 500
        else if (title.contains(baseName)) score += 100
        val sourceYear = sourceYear(sourceName)
        if (year.isNotBlank() && sourceYear == year) score += 1000
        if (anime.contains("【电影】") || anime.contains("【华语电影】")) score += 10
        if (episode.isNotBlank()) {
            val cn = episodeOf(sourceName)
            if (cn != null) score += if (cn.toString() == episode) 200 else -300
        }
        return score
    }

    /**
     * 排序分：基础分 + 平台速度分，仅用于同档候选排序。
     * 服务器返回的 name 形如 `功夫(2004)【华语电影】from iqiyi - 【qiyi】 正片`。
     */
    fun scoreSource(sourceName: String, year: String, baseName: String, episode: String = ""): Int =
        baseScore(sourceName, year, baseName, episode) + platformScore(platformOf(sourceName))
}
