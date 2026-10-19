package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.bean.History
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedMapperTest {

    @Test
    fun mapHistory_buildsCardWithSiteIdAndProgress() {
        val item = History().apply {
            setKey("local@@@1@@@0")
            vodName = "A"
            vodPic = "http://x/a.jpg"
            vodRemarks = "更新至 1 集"
            position = 1_000
            duration = 4_000
        }

        val card = HomeFeedMapper.fromHistory(listOf(item)).single()

        assertEquals("local", card.key)
        assertEquals("1", card.id)
        assertEquals("A", card.title)
        assertEquals("更新至 1 集", card.subtitle)
        assertEquals("http://x/a.jpg", card.posterUrl)
        assertEquals(0.25f, card.progress ?: 0f, 0.0001f)
    }
}
