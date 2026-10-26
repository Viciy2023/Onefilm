package com.onefilm.android.tv.compose.feature.player

import androidx.media3.common.Player

/**
 * 播放器菜单项：纯数据 + 动作，不依赖 Compose，便于单独维护和后续补测试。
 */
data class PlayerMenuEntry(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
)

/**
 * 菜单构建所需的外部动作。PlayerControls 负责注入 UI 状态相关回调，
 * 其余状态（倍速、循环、字幕、音轨等）直接从 PlaybackSession 读取。
 */
class PlayerMenuActions(
    val onPrevious: (() -> Unit)?,
    val onNext: (() -> Unit)?,
    val onOpenEpisodes: () -> Unit,
    val danmakuOn: Boolean,
    val onToggleDanmaku: () -> Unit,
    val onPip: () -> Unit,
    val onDlna: () -> Unit,
)

/** 构建全屏播放器的菜单行（顺序与 0.1.7 保持一致）。 */
fun buildPlayerMenu(actions: PlayerMenuActions): List<PlayerMenuEntry> = buildList {
    add(PlayerMenuEntry("上集", actions.onPrevious != null) { actions.onPrevious?.invoke() })
    add(PlayerMenuEntry("下集", actions.onNext != null) { actions.onNext?.invoke() })
    add(PlayerMenuEntry("重播") { PlaybackSession.replay() })
    add(PlayerMenuEntry("刷新") { PlaybackSession.refresh() })
    add(PlayerMenuEntry("选集", PlaybackSession.queue.size > 1) { actions.onOpenEpisodes() })
    add(PlayerMenuEntry("片头 ${formatTime(PlaybackSession.openingMs)}") { PlaybackSession.markOpening() })
    add(PlayerMenuEntry("片尾 ${formatTime(PlaybackSession.endingMs)}") { PlaybackSession.markEnding() })
    add(PlayerMenuEntry("${PlaybackSession.speed}x") { PlaybackSession.cycleSpeed() })
    add(
        PlayerMenuEntry(
            if (PlaybackSession.repeatMode == Player.REPEAT_MODE_ONE) "循环 开" else "循环 关",
        ) { PlaybackSession.toggleRepeat() },
    )
    add(PlayerMenuEntry(if (actions.danmakuOn) "弹幕 开" else "弹幕 关") { actions.onToggleDanmaku() })
    add(PlayerMenuEntry("画中画") { actions.onPip() })
    add(PlayerMenuEntry("投屏") { actions.onDlna() })
    add(PlayerMenuEntry("画面 ${PlaybackSession.resizeLabel}") { PlaybackSession.cycleResizeMode() })
    add(PlayerMenuEntry("引擎 ${if (PlaybackSession.engineMpv) "MPV" else "Exo"}") {
        PlaybackSession.setEngine(!PlaybackSession.engineMpv)
    })
    add(PlayerMenuEntry("解码 ${PlaybackSession.decodeLabel}") { PlaybackSession.toggleDecode() })
    add(PlayerMenuEntry("渲染 ${PlaybackSession.renderLabel}") { PlaybackSession.toggleRenderMode() })
    add(PlayerMenuEntry("字幕 ${PlaybackSession.subtitleLabel}") { PlaybackSession.cycleSubtitle() })
    add(PlayerMenuEntry("字幕大小 %.1fx".format(PlaybackSession.subtitleScale)) {
        PlaybackSession.cycleSubtitleScale()
    })
    add(PlayerMenuEntry("字幕位置 ${PlaybackSession.subtitlePosition.toInt()}") {
        PlaybackSession.cycleSubtitlePosition()
    })
    add(PlayerMenuEntry("字幕颜色 ${PlaybackSession.subtitleColor}") {
        PlaybackSession.cycleSubtitleColor()
    })
    add(PlayerMenuEntry("音轨 ${PlaybackSession.audioLabel}") { PlaybackSession.cycleAudio() })
    add(PlayerMenuEntry("视轨 ${PlaybackSession.videoLabel}") { PlaybackSession.cycleVideo() })
    add(PlayerMenuEntry("字幕偏移 ${PlaybackSession.textOffsetMs}ms") { PlaybackSession.shiftTextOffset(100L) })
    add(PlayerMenuEntry("音画偏移 ${PlaybackSession.audioOffsetMs}ms") { PlaybackSession.shiftAudioOffset(100L) })
}

/** 时间格式化：菜单与进度标签共用。 */
internal fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
