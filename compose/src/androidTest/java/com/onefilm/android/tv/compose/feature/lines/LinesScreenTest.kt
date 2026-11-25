package com.onefilm.android.tv.compose.feature.lines

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.onefilm.android.tv.compose.bridge.ConfigLine
import com.onefilm.android.tv.compose.bridge.SettingsDataSource
import com.onefilm.android.tv.compose.design.OneFilmTheme
import java.lang.reflect.Proxy
import org.junit.Rule
import org.junit.Test

class LinesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tenLines_areVisible_inOneDialog() {
        val lines = (1..10).map { index ->
            ConfigLine(
                name = "线路$index",
                url = "http://example.com/$index.json",
                current = index == 1,
            )
        }
        compose.setContent {
            OneFilmTheme {
                LinesScreen(
                    dataSource = fakeDataSource(lines),
                    onDone = {},
                )
            }
        }

        compose.onNodeWithText("线路1").assertIsDisplayed()
        compose.onNodeWithText("线路10").assertIsDisplayed()
    }

    private fun fakeDataSource(lines: List<ConfigLine>): SettingsDataSource {
        return Proxy.newProxyInstance(
            SettingsDataSource::class.java.classLoader,
            arrayOf(SettingsDataSource::class.java),
        ) { _, method, args ->
            when (method.name) {
                "allConfigLines" -> lines
                "loadConfig" -> {
                    @Suppress("UNCHECKED_CAST")
                    (args?.getOrNull(3) as? (Boolean, String) -> Unit)
                        ?.invoke(false, "test")
                    Unit
                }
                else -> defaultReturn(method.returnType)
            }
        } as SettingsDataSource
    }

    private fun defaultReturn(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Float.TYPE -> 0f
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        else -> null
    }
}
