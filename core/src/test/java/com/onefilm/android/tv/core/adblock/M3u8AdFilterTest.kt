package com.onefilm.android.tv.core.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M3u8AdFilterTest {

    private fun playlist(vararg segments: String, endList: Boolean = true): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#EXT-X-VERSION:3\n")
        sb.append("#EXT-X-TARGETDURATION:10\n")
        sb.append("#EXT-X-MEDIA-SEQUENCE:0\n")
        segments.forEach { sb.append(it).append('\n') }
        if (endList) sb.append("#EXT-X-ENDLIST\n")
        return sb.toString()
    }

    private fun seg(duration: String, url: String) = "#EXTINF:$duration,\n$url"

    private val lzRule = AdBlockRule(
        "♻️量非",
        listOf("lz", "ffzy"),
        listOf("#EXT-X-DISCONTINUITY\r*\n*#EXTINF:6.666667,[\\s\\S]*?#EXT-X-DISCONTINUITY"),
    )

    @Test
    fun `lz 6_666667 ad block is removed`() {
        val text = playlist(
            seg("10.0", "https://cdn.lz/vod/1.ts"),
            seg("10.0", "https://cdn.lz/vod/2.ts"),
            "#EXT-X-DISCONTINUITY",
            seg("6.666667", "https://ad.lz/ad.ts"),
            "#EXT-X-DISCONTINUITY",
            seg("10.0", "https://cdn.lz/vod/3.ts"),
            seg("10.0", "https://cdn.lz/vod/4.ts"),
            seg("10.0", "https://cdn.lz/vod/5.ts"),
            seg("10.0", "https://cdn.lz/vod/6.ts"),
        )
        val result = M3u8AdFilter.filter("https://cdn.lz/vod/index.m3u8", text, listOf(lzRule))
        assertTrue(result.changed)
        assertEquals("♻️量非", result.rule)
        assertEquals(1, result.removedSegments)
        assertEquals(6.666667, result.removedSeconds, 0.0001)
        assertFalse(result.text.contains("ad.lz/ad.ts"))
        assertTrue(result.text.contains("cdn.lz/vod/3.ts"))
    }

    @Test
    fun `ffzy 6_6 ad block is removed`() {
        val rule = AdBlockRule(
            "♻️量非",
            listOf("ffzy"),
            listOf("#EXT-X-DISCONTINUITY\r*\n*#EXTINF:6.600000,[\\s\\S]*?#EXT-X-DISCONTINUITY"),
        )
        val text = playlist(
            seg("9.0", "https://vip.ffzy/v/1.ts"),
            "#EXT-X-DISCONTINUITY",
            seg("6.6", "https://ad.ffzy/ad.ts"),
            "#EXT-X-DISCONTINUITY",
            seg("9.0", "https://vip.ffzy/v/2.ts"),
            seg("9.0", "https://vip.ffzy/v/3.ts"),
            seg("9.0", "https://vip.ffzy/v/4.ts"),
            seg("9.0", "https://vip.ffzy/v/5.ts"),
        )
        val result = M3u8AdFilter.filter("https://vip.ffzy/v/index.m3u8", text, listOf(rule))
        assertTrue(result.changed)
        assertFalse(result.text.contains("ad.ffzy/ad.ts"))
    }

    @Test
    fun `url rule removes matching segment`() {
        val rule = AdBlockRule("♻️索尼", listOf("suonizy"), listOf("15.1666"))
        val text = playlist(
            seg("9.0", "https://suonizy.com/v/1.ts"),
            seg("15.1666", "https://suonizy.com/15.1666/ad.ts"),
            seg("9.0", "https://suonizy.com/v/2.ts"),
            seg("9.0", "https://suonizy.com/v/3.ts"),
            seg("9.0", "https://suonizy.com/v/4.ts"),
            seg("9.0", "https://suonizy.com/v/5.ts"),
        )
        val result = M3u8AdFilter.filter("https://suonizy.com/v/index.m3u8", text, listOf(rule))
        assertTrue(result.changed)
        assertEquals(1, result.removedSegments)
        assertFalse(result.text.contains("15.1666/ad.ts"))
    }

    @Test
    fun `leshiyun 15_92 is removed`() {
        val rule = AdBlockRule("♻️乐视", listOf("leshiyun"), listOf("15.92"))
        val text = playlist(
            seg("8.0", "https://leshiyun.cn/a/1.ts"),
            seg("15.92", "https://leshiyun.cn/a/15.92.ts"),
            seg("8.0", "https://leshiyun.cn/a/2.ts"),
            seg("8.0", "https://leshiyun.cn/a/3.ts"),
            seg("8.0", "https://leshiyun.cn/a/4.ts"),
            seg("8.0", "https://leshiyun.cn/a/5.ts"),
        )
        val result = M3u8AdFilter.filter("https://leshiyun.cn/a/index.m3u8", text, listOf(rule))
        assertTrue(result.changed)
        assertFalse(result.text.contains("a/15.92.ts"))
    }

    @Test
    fun `live playlist is untouched`() {
        val text = playlist(
            seg("6.0", "https://live.example.com/1.ts"),
            seg("6.0", "https://live.example.com/2.ts"),
            seg("6.0", "https://live.example.com/3.ts"),
            endList = false,
        )
        val result = M3u8AdFilter.filter("https://live.example.com/index.m3u8", text, listOf(lzRule))
        assertFalse(result.changed)
        assertEquals(text, result.text)
    }

    @Test
    fun `master playlist is untouched`() {
        val text = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=1600000\nhigh/index.m3u8\n#EXT-X-ENDLIST\n"
        val result = M3u8AdFilter.filter("https://cdn.example.com/master.m3u8", text, listOf(lzRule))
        assertFalse(result.changed)
        assertEquals(text, result.text)
    }

    @Test
    fun `non m3u8 text is untouched`() {
        val text = "<html>not a playlist</html>"
        val result = M3u8AdFilter.filter("https://cdn.example.com/x.m3u8", text, listOf(lzRule))
        assertFalse(result.changed)
        assertEquals(text, result.text)
    }

    @Test
    fun `heuristic removes minority segment when no rule matches`() {
        val text = playlist(
            seg("8.0", "https://cdn.example.com/vod/seg-1.ts"),
            seg("8.0", "https://cdn.example.com/vod/seg-2.ts"),
            seg("8.0", "https://cdn.example.com/vod/seg-3.ts"),
            seg("8.0", "https://cdn.example.com/vod/seg-4.ts"),
            seg("8.0", "https://ad.example.net/x/ad.ts"),
            seg("8.0", "https://cdn.example.com/vod/seg-5.ts"),
            seg("8.0", "https://cdn.example.com/vod/seg-6.ts"),
        )
        val result = M3u8AdFilter.filter("https://cdn.example.com/vod/index.m3u8", text, emptyList())
        assertTrue(result.changed)
        assertTrue(result.heuristic)
        assertFalse(result.text.contains("ad.example.net/x/ad.ts"))
    }

    @Test
    fun `heuristic keeps playlist when no clear majority`() {
        val text = playlist(
            seg("8.0", "https://a1.example.com/1.ts"),
            seg("8.0", "https://a2.example.com/2.ts"),
            seg("8.0", "https://a3.example.com/3.ts"),
            seg("8.0", "https://a4.example.com/4.ts"),
            seg("8.0", "https://a5.example.com/5.ts"),
        )
        val result = M3u8AdFilter.filter("https://a1.example.com/index.m3u8", text, emptyList())
        assertFalse(result.changed)
    }

    @Test
    fun `safety valve rejects removing too many segments`() {
        val rule = AdBlockRule("test", listOf("example.com"), listOf("ad"))
        val text = playlist(
            seg("8.0", "https://cdn.example.com/v/ad-1.ts"),
            seg("8.0", "https://cdn.example.com/v/ad-2.ts"),
            seg("8.0", "https://cdn.example.com/v/ad-3.ts"),
            seg("8.0", "https://cdn.example.com/v/1.ts"),
            seg("8.0", "https://cdn.example.com/v/2.ts"),
            seg("8.0", "https://cdn.example.com/v/3.ts"),
        )
        val result = M3u8AdFilter.filter("https://cdn.example.com/v/index.m3u8", text, listOf(rule))
        assertFalse(result.changed)
        assertEquals(text, result.text)
    }

    @Test
    fun `key and map tags are preserved`() {
        val text = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-KEY:METHOD=AES-128,URI=\"k.bin\"\n#EXT-X-MAP:URI=\"init.mp4\"\n" +
            seg("10.0", "https://cdn.lz/v/1.ts") + "\n" +
            "#EXT-X-DISCONTINUITY\n" + seg("6.666667", "https://ad.lz/ad.ts") + "\n" +
            "#EXT-X-DISCONTINUITY\n" + seg("10.0", "https://cdn.lz/v/2.ts") + "\n" +
            seg("10.0", "https://cdn.lz/v/3.ts") + "\n" +
            seg("10.0", "https://cdn.lz/v/4.ts") + "\n" +
            seg("10.0", "https://cdn.lz/v/5.ts") + "\n#EXT-X-ENDLIST\n"
        val result = M3u8AdFilter.filter("https://cdn.lz/v/index.m3u8", text, listOf(lzRule))
        assertTrue(result.changed)
        assertTrue(result.text.contains("#EXT-X-KEY:METHOD=AES-128"))
        assertTrue(result.text.contains("#EXT-X-MAP:URI=\"init.mp4\""))
    }

    @Test
    fun `crlf playlist keeps crlf`() {
        val text = playlist(
            seg("10.0", "https://cdn.lz/v/1.ts"),
            "#EXT-X-DISCONTINUITY",
            seg("6.666667", "https://ad.lz/ad.ts"),
            "#EXT-X-DISCONTINUITY",
            seg("10.0", "https://cdn.lz/v/2.ts"),
            seg("10.0", "https://cdn.lz/v/3.ts"),
            seg("10.0", "https://cdn.lz/v/4.ts"),
            seg("10.0", "https://cdn.lz/v/5.ts"),
        ).replace("\n", "\r\n")
        val result = M3u8AdFilter.filter("https://cdn.lz/v/index.m3u8", text, listOf(lzRule))
        assertTrue(result.changed)
        assertTrue(result.text.contains("\r\n"))
        assertFalse(result.text.contains("ad.lz/ad.ts"))
    }

    @Test
    fun `youzhi 16_63 url rule`() {
        val rule = AdBlockRule("♻️优质", listOf("yzzy", "playback"), listOf("16.63", "18.66", "17.66", "19.13"))
        val text = playlist(
            seg("8.0", "https://playback.example.com/v/1.ts"),
            seg("16.63", "https://playback.example.com/v/16.63.ts"),
            seg("8.0", "https://playback.example.com/v/2.ts"),
            seg("8.0", "https://playback.example.com/v/3.ts"),
            seg("8.0", "https://playback.example.com/v/4.ts"),
            seg("8.0", "https://playback.example.com/v/5.ts"),
        )
        val result = M3u8AdFilter.filter("https://playback.example.com/v/index.m3u8", text, listOf(rule))
        assertTrue(result.changed)
        assertFalse(result.text.contains("v/16.63.ts"))
    }

    @Test
    fun `kuaikan 15_32 url rule`() {
        val rule = AdBlockRule("♻️快看", listOf("kuaikan", "vip.kuaikan"), listOf("15.32", "15.231", "18.066"))
        val text = playlist(
            seg("8.0", "https://vip.kuaikan.com/v/1.ts"),
            seg("15.32", "https://vip.kuaikan.com/v/15.32.ts"),
            seg("8.0", "https://vip.kuaikan.com/v/2.ts"),
            seg("8.0", "https://vip.kuaikan.com/v/3.ts"),
            seg("8.0", "https://vip.kuaikan.com/v/4.ts"),
            seg("8.0", "https://vip.kuaikan.com/v/5.ts"),
        )
        val result = M3u8AdFilter.filter("https://vip.kuaikan.com/v/index.m3u8", text, listOf(rule))
        assertTrue(result.changed)
        assertFalse(result.text.contains("v/15.32.ts"))
    }

    @Test
    fun `360 hrz8QcR9 text rule`() {
        val rule = AdBlockRule("♻️360", listOf("lyhuicheng"), listOf("#EXTINF.*?\\s+.*?hrz8QcR9.*?\\.ts\\s+"))
        val text = playlist(
            seg("8.0", "https://cdn.lyhuicheng.com/v/1.ts"),
            seg("8.0", "https://cdn.lyhuicheng.com/hrz8QcR9/ad.ts"),
            seg("8.0", "https://cdn.lyhuicheng.com/v/2.ts"),
            seg("8.0", "https://cdn.lyhuicheng.com/v/3.ts"),
            seg("8.0", "https://cdn.lyhuicheng.com/v/4.ts"),
            seg("8.0", "https://cdn.lyhuicheng.com/v/5.ts"),
        )
        val result = M3u8AdFilter.filter("https://cdn.lyhuicheng.com/v/index.m3u8", text, listOf(rule))
        assertTrue(result.changed)
        assertFalse(result.text.contains("hrz8QcR9"))
    }

    @Test
    fun `opensource 5s ad with method none key is removed`() {
        val rule = AdBlockRule(
            "♻️开源棋牌",
            listOf("askzycdn"),
            listOf("#EXT-X-KEY:METHOD=NONE*\\n*#EXTINF:5,[\\s\\S]*?#EXT-X-DISCONTINUITY"),
        )
        val text = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n" +
            "#EXT-X-KEY:METHOD=AES-128,URI=\"k.bin\"\n" +
            seg("8.0", "https://cdn.askzycdn.com/v/1.ts") + "\n" +
            "#EXT-X-KEY:METHOD=NONE\n" + seg("5", "https://ad.askzycdn.com/ad.ts") + "\n" +
            "#EXT-X-DISCONTINUITY\n" +
            seg("8.0", "https://cdn.askzycdn.com/v/2.ts") + "\n" +
            seg("8.0", "https://cdn.askzycdn.com/v/3.ts") + "\n" +
            seg("8.0", "https://cdn.askzycdn.com/v/4.ts") + "\n" +
            seg("8.0", "https://cdn.askzycdn.com/v/5.ts") + "\n#EXT-X-ENDLIST\n"
        val result = M3u8AdFilter.filter("https://cdn.askzycdn.com/v/index.m3u8", text, listOf(rule))
        assertTrue(result.changed)
        assertFalse(result.text.contains("ad.askzycdn.com/ad.ts"))
        assertTrue(result.text.contains("#EXT-X-KEY:METHOD=AES-128"))
    }

    @Test
    fun `storm adjump text rule`() {
        val rule = AdBlockRule("♻️暴风", listOf("bfengbf.com"), listOf("#EXTINF.*?\\s+.*?adjump.*?\\.ts\\s+"))
        val text = playlist(
            seg("8.0", "https://cdn.bfengbf.com/v/1.ts"),
            seg("8.0", "https://cdn.bfengbf.com/adjump/ad.ts"),
            seg("8.0", "https://cdn.bfengbf.com/v/2.ts"),
            seg("8.0", "https://cdn.bfengbf.com/v/3.ts"),
            seg("8.0", "https://cdn.bfengbf.com/v/4.ts"),
            seg("8.0", "https://cdn.bfengbf.com/v/5.ts"),
        )
        val result = M3u8AdFilter.filter("https://cdn.bfengbf.com/v/index.m3u8", text, listOf(rule))
        assertTrue(result.changed)
        assertFalse(result.text.contains("adjump"))
    }

    @Test
    fun `long playlist beyond old cap still removes foreign prefix segment`() {
        val main = (1..500).map { seg("4.0", "https://cdn.ffzy/v/seg_$it.ts") }
        val ad = seg("4.0", "https://ad.other.net/ad_01.ts")
        val text = playlist(*(main + ad).toTypedArray())

        val result = M3u8AdFilter.filter("https://cdn.ffzy/v/index.m3u8", text, emptyList())
        assertTrue(result.changed)
        assertTrue(result.heuristic)
        assertEquals(1, result.removedSegments)
        assertFalse(result.text.contains("ad.other.net"))
        assertTrue(result.text.contains("cdn.ffzy/v/seg_500.ts"))
    }

    @Test
    fun `encrypted playlist removes short middle group between long groups`() {
        val g1 = (1..10).map { seg("60.0", "https://cdn.ql/v/seg_$it.ts") }
        val ad = (11..14).map { seg("5.0", "https://cdn.ql/v/seg_$it.ts") }
        val g2 = (15..24).map { seg("60.0", "https://cdn.ql/v/seg_$it.ts") }
        val text = buildString {
            append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:60\n")
            append("#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n")
            append(g1.joinToString("\n")).append('\n')
            append("#EXT-X-DISCONTINUITY\n")
            append(ad.joinToString("\n")).append('\n')
            append("#EXT-X-DISCONTINUITY\n")
            append(g2.joinToString("\n")).append('\n')
            append("#EXT-X-ENDLIST\n")
        }

        val result = M3u8AdFilter.filter("https://cdn.ql/v/index.m3u8", text, emptyList())
        assertTrue(result.changed)
        assertEquals(4, result.removedSegments)
        assertEquals(20.0, result.removedSeconds, 0.001)
        assertTrue(result.text.contains("seg_10.ts"))
        assertTrue(result.text.contains("seg_15.ts"))
    }

    @Test
    fun `short group with short neighbour is kept`() {
        val g1 = (1..10).map { seg("60.0", "https://cdn.ql/v/s_$it.ts") }
        val short1 = (11..16).map { seg("10.0", "https://cdn.ql/v/s_$it.ts") }
        val short2 = (17..22).map { seg("10.0", "https://cdn.ql/v/s_$it.ts") }
        val text = buildString {
            append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:60\n")
            append("#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n")
            append(g1.joinToString("\n")).append('\n')
            append("#EXT-X-DISCONTINUITY\n")
            append(short1.joinToString("\n")).append('\n')
            append("#EXT-X-DISCONTINUITY\n")
            append(short2.joinToString("\n")).append('\n')
            append("#EXT-X-ENDLIST\n")
        }

        val result = M3u8AdFilter.filter("https://cdn.ql/v/index.m3u8", text, emptyList())
        assertFalse(result.changed)
    }
}
