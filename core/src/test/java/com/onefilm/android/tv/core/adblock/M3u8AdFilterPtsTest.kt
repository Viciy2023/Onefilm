package com.onefilm.android.tv.core.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class M3u8AdFilterPtsTest {

    private fun group(segments: Int, secondsEach: Double, name: String): String =
        (0 until segments).joinToString("\n") {
            "#EXTINF:$secondsEach,\nhttps://cdn.ffzy/v/$name$it.ts"
        }

    private fun playlist(groups: List<String>, extraTags: List<String> = emptyList()): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#EXT-X-VERSION:3\n")
        sb.append("#EXT-X-TARGETDURATION:12\n")
        extraTags.forEach { sb.append(it).append('\n') }
        sb.append(groups.joinToString("\n#EXT-X-DISCONTINUITY\n"))
        sb.append("\n#EXT-X-ENDLIST\n")
        return sb.toString()
    }

    /** 与 ffzy 实测一致的 5 组结构（PTS 单位：毫秒）。 */
    private fun ffzyGroups() = listOf(
        group(100, 3.5724, "g0_"),
        group(5, 3.926, "g1_"),
        group(300, 4.3958667, "g2_"),
        group(4, 4.34, "g3_"),
        group(100, 11.03, "g4_"),
    )

    private val ffzyPts = mapOf(
        "https://cdn.ffzy/v/g0_0.ts" to 1_480L,
        "https://cdn.ffzy/v/g1_0.ts" to 1_467L,
        "https://cdn.ffzy/v/g2_0.ts" to 358_720L,
        "https://cdn.ffzy/v/g3_0.ts" to 1_480L,
        "https://cdn.ffzy/v/g4_0.ts" to 1_677_480L,
    )

    @Test
    fun `pts stage removes ffzy style ads`() {
        val text = playlist(ffzyGroups())
        val calls = AtomicInteger()
        val probe = M3u8AdFilter.PtsProbe { urls ->
            calls.addAndGet(1)
            urls.map { ffzyPts[it] }
        }

        val result = M3u8AdFilter.filter("https://cdn.ffzy/v/index.m3u8", text, emptyList(), probe = probe)

        assertTrue(result.changed)
        assertTrue(result.pts)
        assertFalse(result.heuristic)
        assertNull(result.rule)
        assertEquals(1, calls.get())
        assertEquals(9, result.removedSegments)
        assertEquals(36.99, result.removedSeconds, 0.01)
        assertFalse(result.text.contains("g1_"))
        assertFalse(result.text.contains("g3_"))
        assertTrue(result.text.contains("g0_0.ts"))
        assertTrue(result.text.contains("g2_0.ts"))
        assertTrue(result.text.contains("g4_0.ts"))
    }

    @Test
    fun `rule hit does not trigger any probe`() {
        val rule = AdBlockRule(
            "♻️量非",
            listOf("ffzy"),
            listOf("#EXT-X-DISCONTINUITY\r*\n*#EXTINF:6.600000,[\\s\\S]*?#EXT-X-DISCONTINUITY"),
        )
        val text = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n" +
            "#EXTINF:9.0,\nhttps://cdn.ffzy/v/1.ts\n" +
            "#EXT-X-DISCONTINUITY\n" +
            "#EXTINF:6.600000,\nhttps://ad.ffzy/ad.ts\n" +
            "#EXT-X-DISCONTINUITY\n" +
            "#EXTINF:9.0,\nhttps://cdn.ffzy/v/2.ts\n" +
            "#EXTINF:9.0,\nhttps://cdn.ffzy/v/3.ts\n" +
            "#EXTINF:9.0,\nhttps://cdn.ffzy/v/4.ts\n" +
            "#EXTINF:9.0,\nhttps://cdn.ffzy/v/5.ts\n" +
            "#EXT-X-ENDLIST\n"
        val calls = AtomicInteger()
        val probe = M3u8AdFilter.PtsProbe { urls ->
            calls.addAndGet(1)
            urls.map { null }
        }

        val result = M3u8AdFilter.filter("https://cdn.ffzy/v/index.m3u8", text, listOf(rule), probe = probe)

        assertTrue(result.changed)
        assertEquals("♻️量非", result.rule)
        assertEquals(0, calls.get())
    }

    @Test
    fun `encrypted playlists are never probed`() {
        val text = playlist(
            ffzyGroups(),
            listOf("#EXT-X-KEY:METHOD=AES-128,URI=\"https://cdn.ffzy/key\""),
        )
        val calls = AtomicInteger()
        val probe = M3u8AdFilter.PtsProbe { urls ->
            calls.addAndGet(1)
            urls.map { ffzyPts[it] }
        }

        val result = M3u8AdFilter.filter("https://cdn.ffzy/v/index.m3u8", text, emptyList(), probe = probe)
        assertFalse(result.changed)
        assertEquals(0, calls.get())
    }

    @Test
    fun `too many groups are skipped`() {
        val groups = (0 until 9).map { group(2, 5.0, "n${it}_") }
        val text = playlist(groups)
        val calls = AtomicInteger()
        val probe = M3u8AdFilter.PtsProbe { urls ->
            calls.addAndGet(1)
            urls.map { null }
        }

        val result = M3u8AdFilter.filter("https://cdn.ffzy/v/index.m3u8", text, emptyList(), probe = probe)
        assertFalse(result.changed)
        assertEquals(0, calls.get())
    }

    @Test
    fun `inconclusive probe leaves playlist untouched`() {
        val text = playlist(ffzyGroups())
        val probe = M3u8AdFilter.PtsProbe { urls -> urls.map { null } }

        val result = M3u8AdFilter.filter("https://cdn.ffzy/v/index.m3u8", text, emptyList(), probe = probe)
        assertFalse(result.changed)
        assertEquals(text, result.text)
    }

    @Test
    fun `ad break longer than 60s is rejected by safety valve`() {
        // 片段数 > 400 以跳过前缀聚类，确保进入 PTS 阶段
        val groups = listOf(
            group(200, 0.5, "main0_"),
            group(61, 1.0, "ad_"),
            group(200, 0.5, "main1_"),
        )
        val pts = mapOf(
            "https://cdn.ffzy/v/main0_0.ts" to 100_000L,
            "https://cdn.ffzy/v/ad_0.ts" to 1_480L,
            "https://cdn.ffzy/v/main1_0.ts" to 161_000L,
        )
        val text = playlist(groups)
        val probe = M3u8AdFilter.PtsProbe { urls -> urls.map { pts[it] } }

        val result = M3u8AdFilter.filter("https://cdn.ffzy/v/index.m3u8", text, emptyList(), probe = probe)
        assertFalse(result.changed)
        assertEquals(text, result.text)
    }
}
