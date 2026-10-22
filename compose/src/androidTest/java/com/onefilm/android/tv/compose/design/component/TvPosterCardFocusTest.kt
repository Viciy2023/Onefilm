package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.onefilm.android.tv.compose.design.OneFilmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvPosterCardFocusTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun focusingCard_doesNotMoveItselfOrSibling() {
        compose.setContent {
            OneFilmTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvPosterCard(
                        title = "第一张",
                        posterUrl = null,
                        subtitle = null,
                        onClick = {},
                        modifier = Modifier.width(200.dp).testTag("card1"),
                    )
                    TvPosterCard(
                        title = "第二张",
                        posterUrl = null,
                        subtitle = null,
                        onClick = {},
                        modifier = Modifier.width(200.dp).testTag("card2"),
                    )
                }
            }
        }

        val before1 = compose.onNodeWithTag("card1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val before2 = compose.onNodeWithTag("card2", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        compose.onNodeWithTag("card1", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        compose.onNodeWithTag("card1", useUnmergedTree = true).assertIsFocused()

        val after1 = compose.onNodeWithTag("card1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val after2 = compose.onNodeWithTag("card2", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertEquals(before1.left.value, after1.left.value, 0.5f)
        assertEquals(before1.top.value, after1.top.value, 0.5f)
        assertEquals(before1.right.value, after1.right.value, 0.5f)
        assertEquals(before1.bottom.value, after1.bottom.value, 0.5f)

        assertEquals(before2.left.value, after2.left.value, 0.5f)
        assertEquals(before2.top.value, after2.top.value, 0.5f)
        assertEquals(before2.right.value, after2.right.value, 0.5f)
        assertEquals(before2.bottom.value, after2.bottom.value, 0.5f)
    }
}
