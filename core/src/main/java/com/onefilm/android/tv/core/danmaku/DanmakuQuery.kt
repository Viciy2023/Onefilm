package com.onefilm.android.tv.core.danmaku

import java.util.Locale

/**
 * 弹幕搜索的标题/集名候选（纯逻辑，供 TV 与手机共用）。
 *
 * 片源标题常带“粤语/国语”等后缀，集名可能是“正片/HD中字/01/第1集”，
 * 直接丢给弹幕 API 往往匹配不到。这里生成一组由精确到宽松的候选。
 */
object DanmakuQuery {

    private val NAME_SUFFIX = Regex("[\\(（]?(粤语|国语|普通话|英语|粤|国)[\\)）]?\$")

    private val EPISODE_ALIASES = listOf("正片", "HD中字", "第1集", "第01集", "1")

    fun nameCandidates(name: String): List<String> {
        val cleaned = name.replace(NAME_SUFFIX, "").trim()
        return listOf(cleaned, name).distinct().filter { it.isNotBlank() }
    }

    fun episodeCandidates(episode: String): List<String> {
        val items = LinkedHashSet<String>()
        if (episode.isNotBlank()) items.add(episode)
        val number = Regex("\\d+").find(episode)?.value?.toIntOrNull()
        if (number != null) {
            items.add("第${number}集")
            items.add(number.toString())
            items.add(String.format(Locale.US, "%02d", number))
        }
        items.addAll(EPISODE_ALIASES)
        return items.filter { it.isNotBlank() }
    }

    /** 按“先清洗标题、再尝试不同集名”的顺序组合，最多 [limit] 组。 */
    fun combinations(name: String, episode: String, limit: Int = 6): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val names = nameCandidates(name)
        val episodes = episodeCandidates(episode)
        outer@ for (n in names) {
            for (e in episodes) {
                out.add(n to e)
                if (out.size >= limit) break@outer
            }
        }
        return out
    }
}
