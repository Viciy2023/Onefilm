package com.onefilm.android.tv.composebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索设置的纯逻辑测试。
 *
 * 不触碰 Android 的 SharedPreferences，只验证可测试的取值与编解码。
 */
class SearchSettingsTest {

    @Test
    fun allowedThreadCountsAreKept() {
        listOf(12, 16, 24, 36, 48).forEach { value ->
            assertEquals(value, SearchSettings.normalizeThreads(value))
        }
    }

    @Test
    fun otherThreadCountsFallBackToDefault() {
        listOf(0, 1, 6, 15, 17, 32, 64, 999, -4).forEach { value ->
            assertEquals(SearchSettings.DEFAULT_THREADS, SearchSettings.normalizeThreads(value))
        }
    }

    @Test
    fun disabledSitesRoundTrip() {
        val keys = linkedSetOf("bilibili", "weibo", "alist")
        val encoded = SearchSettings.encodeDisabled(keys)
        assertEquals(keys, SearchSettings.decodeDisabled(encoded))
    }

    @Test
    fun emptyRawMeansEverythingEnabled() {
        assertTrue(SearchSettings.decodeDisabled("").isEmpty())
        assertTrue(SearchSettings.decodeDisabled("   ").isEmpty())
    }

    @Test
    fun blankAndDuplicateEntriesAreIgnored() {
        val decoded = SearchSettings.decodeDisabled("a\n\n b \na\n  \nc")
        assertEquals(setOf("a", "b", "c"), decoded)
    }

    @Test
    fun membershipCheckIsExact() {
        val disabled = SearchSettings.decodeDisabled("qq\nmgtv")
        assertTrue("qq" in disabled)
        assertTrue("mgtv" in disabled)
        assertFalse("qqq" in disabled)
        assertFalse("qq " in disabled)
    }
}
