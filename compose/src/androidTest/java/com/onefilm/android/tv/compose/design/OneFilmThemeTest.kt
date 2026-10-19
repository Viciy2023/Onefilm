package com.onefilm.android.tv.compose.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OneFilmThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun darkTheme_uses_gray_background_and_white_accent() {
        var background: Color? = null
        var accent: Color? = null
        compose.setContent {
            OneFilmTheme(darkTheme = true) {
                background = MaterialTheme.colorScheme.background
                accent = MaterialTheme.colorScheme.primary
            }
        }
        assertEquals(Color(0xFF1C1C1E), background)
        assertEquals(Color(0xFFF2F2F7), accent)
    }

    @Test
    fun lightTheme_has_dark_text_on_light_background() {
        var background: Color? = null
        var onBackground: Color? = null
        compose.setContent {
            OneFilmTheme(darkTheme = false) {
                background = MaterialTheme.colorScheme.background
                onBackground = MaterialTheme.colorScheme.onBackground
            }
        }
        assertEquals(Color(0xFFF2F2F7), background)
        assertEquals(Color(0xFF1C1C1E), onBackground)
    }
}
