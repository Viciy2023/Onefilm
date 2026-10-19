package com.onefilm.android.tv.compose.design.component

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.onefilm.android.tv.compose.design.OneFilmTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun posterCard_showsTitle_andHandlesClick() {
        var clicked = false
        compose.setContent {
            OneFilmTheme {
                PosterCard(title = "测试影片", posterUrl = null, subtitle = "2026", onClick = { clicked = true })
            }
        }
        compose.onNodeWithContentDescription("测试影片").assertHasClickAction().performClick()
        assertTrue(clicked)
    }

    @Test
    fun hero_showsTitle_andPlayButton() {
        compose.setContent {
            OneFilmTheme {
                HeroBanner(title = "Hero 标题", description = "简介", backdropUrl = null, onPlay = {}, onDetails = {})
            }
        }
        compose.onNodeWithText("Hero 标题").assertIsDisplayed()
        compose.onNodeWithText("播放").assertIsDisplayed()
    }
}
