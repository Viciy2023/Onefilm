package com.onefilm.android.tv.compose.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放器菜单与时间格式的回归测试。
 *
 * 这些是纯逻辑：不依赖 Compose 组合，也不依赖设备。
 */
class PlayerMenuTest {

    private fun actions(
        onPrevious: (() -> Unit)? = {},
        onNext: (() -> Unit)? = {},
        danmakuOn: Boolean = true,
        onToggleDanmaku: () -> Unit = {},
        onOpenEpisodes: () -> Unit = {},
    ) = PlayerMenuActions(
        onPrevious = onPrevious,
        onNext = onNext,
        onOpenEpisodes = onOpenEpisodes,
        danmakuOn = danmakuOn,
        onToggleDanmaku = onToggleDanmaku,
        onPip = {},
        onDlna = {},
    )

    @Test
    fun menuKeepsStableOrderAndCount() {
        val labels = buildPlayerMenu(actions()).map { it.label }
        assertEquals(24, labels.size)
        assertEquals("上集", labels[0])
        assertEquals("下集", labels[1])
        assertEquals("重播", labels[2])
        assertEquals("刷新", labels[3])
        assertEquals("选集", labels[4])
        assertTrue(labels.any { it.startsWith("片头") })
        assertTrue(labels.any { it.startsWith("片尾") })
        assertTrue(labels.any { it.startsWith("画面") })
        assertTrue(labels.any { it.startsWith("音轨") })
        assertTrue(labels.any { it.startsWith("字幕偏移") })
    }

    @Test
    fun previousAndNextDisabledWithoutCallbacks() {
        val labels = buildPlayerMenu(actions(onPrevious = null, onNext = null))
        val previous = labels.first { it.label == "上集" }
        val next = labels.first { it.label == "下集" }
        assertFalse(previous.enabled)
        assertFalse(next.enabled)
    }

    @Test
    fun previousAndNextInvokeCallbacks() {
        var previousCalls = 0
        var nextCalls = 0
        val labels = buildPlayerMenu(
            actions(
                onPrevious = { previousCalls++ },
                onNext = { nextCalls++ },
            ),
        )
        labels.first { it.label == "上集" }.onClick()
        labels.first { it.label == "下集" }.onClick()
        assertEquals(1, previousCalls)
        assertEquals(1, nextCalls)
    }

    @Test
    fun danmakuLabelFollowsStateAndInvokesCallback() {
        var toggles = 0
        val onLabels = buildPlayerMenu(actions(danmakuOn = true, onToggleDanmaku = { toggles++ }))
        val offLabels = buildPlayerMenu(actions(danmakuOn = false, onToggleDanmaku = { toggles++ }))

        assertEquals("弹幕 开", onLabels.first { it.label.startsWith("弹幕") }.label)
        assertEquals("弹幕 关", offLabels.first { it.label.startsWith("弹幕") }.label)

        onLabels.first { it.label.startsWith("弹幕") }.onClick()
        assertEquals(1, toggles)
    }

    @Test
    fun episodesEntryOpensCallback() {
        var opened = 0
        val labels = buildPlayerMenu(actions(onOpenEpisodes = { opened++ }))
        labels.first { it.label == "选集" }.onClick()
        assertEquals(1, opened)
    }

    @Test
    fun formatTimeHandlesMinutesAndHours() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("00:59", formatTime(59_000L))
        assertEquals("01:00", formatTime(60_000L))
        assertEquals("59:59", formatTime(3_599_000L))
        assertEquals("1:00:00", formatTime(3_600_000L))
        assertEquals("2:40:25", formatTime(9_625_000L))
        assertEquals("00:00", formatTime(-5_000L))
    }
}
