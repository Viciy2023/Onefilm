package com.onefilm.android.tv.compose.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.onefilm.android.tv.compose.bridge.HomeFeed
import com.onefilm.android.tv.compose.bridge.HomeSection
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.OneFilmTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun content_showsHero() {
        val feed = HomeFeed(
            hero = listOf(MediaCard("local", "1", "Hero 标题", null, null)),
            sections = emptyList(),
        )
        compose.setContent {
            OneFilmTheme {
                HomeScreen(HomeUiState.Content(feed), onRetry = {}, onCardClick = {})
            }
        }
        compose.onNodeWithText("Hero 标题").assertIsDisplayed()
    }

    @Test
    fun content_withoutHero_showsSectionTitle() {
        val feed = HomeFeed(
            hero = emptyList(),
            sections = listOf(HomeSection("推荐", listOf(MediaCard("local", "1", "A", null, null)))),
        )
        compose.setContent {
            OneFilmTheme {
                HomeScreen(HomeUiState.Content(feed), onRetry = {}, onCardClick = {})
            }
        }
        compose.onNodeWithText("推荐").assertIsDisplayed()
    }

    @Test
    fun empty_showsMessageAndRetry() {
        var retried = false
        compose.setContent {
            OneFilmTheme {
                HomeScreen(HomeUiState.Empty, onRetry = { retried = true }, onCardClick = {})
            }
        }
        compose.onNodeWithText("暂无内容").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }

    @Test
    fun error_showsMessageAndRetry() {
        var retried = false
        compose.setContent {
            OneFilmTheme {
                HomeScreen(HomeUiState.Error(), onRetry = { retried = true }, onCardClick = {})
            }
        }
        compose.onNodeWithText("加载失败，请重试").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }
}
