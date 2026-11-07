package com.onefilm.android.tv.core.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPtsContinuityTest {

    private fun group(
        index: Int,
        durationMillis: Long,
        ptsMillis: Long?,
        segments: Int = 1,
    ) = HlsPtsContinuity.Group(
        index = index,
        startLine = index * 10,
        endLine = index * 10 + 1,
        durationMillis = durationMillis,
        segmentCount = segments,
        firstSegmentUrl = "https://cdn.example/g$index.ts",
        ptsMillis = ptsMillis,
    )

    @Test
    fun `ffzy style pre-roll and mid-roll are detected`() {
        val groups = listOf(
            group(0, 357_240, 1_480, 89),
            group(1, 19_630, 1_467, 5),
            group(2, 1_318_760, 358_720, 330),
            group(3, 17_360, 1_480, 4),
            group(4, 1_103_000, 1_677_480, 275),
        )
        val verdict = HlsPtsContinuity.classify(groups)
        assertEquals(setOf(1, 3), verdict?.adGroups)
        assertEquals(setOf(0, 2, 4), verdict?.mainGroups)
    }

    @Test
    fun `continuous timeline has no ads`() {
        val groups = listOf(
            group(0, 100_000, 0),
            group(1, 100_000, 100_000),
            group(2, 100_000, 200_000),
        )
        val verdict = HlsPtsContinuity.classify(groups)
        assertEquals(emptySet<Int>(), verdict?.adGroups)
    }

    @Test
    fun `fewer than two probed groups is inconclusive`() {
        val groups = listOf(
            group(0, 100_000, null),
            group(1, 100_000, 1_480),
            group(2, 100_000, null),
        )
        assertNull(HlsPtsContinuity.classify(groups))
    }

    @Test
    fun `ad break length merges adjacent groups`() {
        val groups = listOf(
            group(0, 100_000, 0),
            group(1, 20_000, 1_480),
            group(2, 25_000, 21_480),
            group(3, 100_000, 46_480),
        )
        assertEquals(
            45_000L,
            HlsPtsContinuity.longestAdBreakMillis(groups, setOf(1, 2)),
        )
    }
}
