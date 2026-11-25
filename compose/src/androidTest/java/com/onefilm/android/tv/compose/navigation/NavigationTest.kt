package com.onefilm.android.tv.compose.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.onefilm.android.tv.compose.design.OneFilmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun adaptiveNavigation_showsContent_andNavigates() {
        var target: String? = null
        compose.setContent {
            OneFilmTheme {
                AdaptiveNavigation(isTv = false, currentRoute = Routes.HOME, onNavigate = { target = it }) {
                    Text("CONTENT")
                }
            }
        }
        compose.onNodeWithText("CONTENT").assertIsDisplayed()
        compose.onNodeWithText("搜索").performClick()
        assertEquals(Routes.SEARCH, target)
    }
}
