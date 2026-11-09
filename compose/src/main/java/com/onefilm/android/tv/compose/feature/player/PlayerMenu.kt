package com.onefilm.android.tv.compose.feature.player

import android.content.Context
import androidx.media3.common.Player
import com.onefilm.android.tv.compose.R

/**
 * 播放器菜单项：纯数据 + 动作，不依赖 Compose，便于单独维护和后续补测试。
 */
data class PlayerMenuEntry(
    val label: String,
    val enabled: Boolean = true,
    /** 遥控器长按（用于片头/片尾重置）。必须放在 onClick 前面，否则尾随 lambda 会绑到长按上。 */
    val onLongClick: (() -> Unit)? = null,
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
    /** 打开「解析」引擎选择面板；没有可用解析引擎时为 null。 */
    val onParse: (() -> Unit)?,
    /** 打开「清晰度」选择面板；只有一个地址时为 null。 */
    val onOpenQuality: (() -> Unit)?,
    /** 手动换线路；详情页没提供多线路时为 null。 */
    val onSwitchLine: (() -> Unit)?,
    /** 打开轨道面板（音轨/字幕/视频轨）；没有轨道时为 null。 */
    val onOpenTracks: (() -> Unit)?,
    /** 打开弹幕设置面板。 */
    val onOpenDanmaku: (() -> Unit)?,
    /** 在线搜索字幕（未配置字幕接口 token 时为 null）。 */
    val onSearchSubtitle: (() -> Unit)?,
    /** 导入本地字幕文件。 */
    val onImportSubtitle: (() -> Unit)?,
    val danmakuOn: Boolean,
    val onToggleDanmaku: () -> Unit,
    val onPip: () -> Unit,
    val onDlna: () -> Unit,
)

/** 构建全屏播放器的菜单行（顺序与 0.1.7 保持一致）。 */
fun buildPlayerMenu(actions: PlayerMenuActions, context: Context): List<PlayerMenuEntry> = buildList {
    fun s(id: Int, vararg args: Any): String = context.getString(id, *args)
    val defaultLabel = s(R.string.common_default)
    // 第一排固定顺序：上集-下集-片头-片尾-快进-弹幕-刷新-重播，其余按使用频率排列。
    add(PlayerMenuEntry(s(R.string.player_prev_ep), actions.onPrevious != null) { actions.onPrevious?.invoke() })
    add(PlayerMenuEntry(s(R.string.player_next_ep), actions.onNext != null) { actions.onNext?.invoke() })
    add(
        PlayerMenuEntry(
            s(R.string.player_opening, formatTime(PlaybackSession.openingMs)),
            onClick = { PlaybackSession.markOpening() },
            onLongClick = { PlaybackSession.clearOpening() },
        ),
    )
    add(
        PlayerMenuEntry(
            s(R.string.player_ending, formatTime(PlaybackSession.endingMs)),
            onClick = { PlaybackSession.markEnding() },
            onLongClick = { PlaybackSession.clearEnding() },
        ),
    )
    add(PlayerMenuEntry("${s(R.string.player_forward)} 5s") { PlaybackSession.seekBy(5_000L) })
    add(PlayerMenuEntry(if (actions.danmakuOn) s(R.string.player_danmaku_on) else s(R.string.player_danmaku_off)) { actions.onToggleDanmaku() })
    add(PlayerMenuEntry(s(R.string.player_refresh)) { PlaybackSession.refresh() })
    add(PlayerMenuEntry(s(R.string.player_replay)) { PlaybackSession.replay() })

    add(PlayerMenuEntry(s(R.string.player_select_episode), PlaybackSession.queue.size > 1) { actions.onOpenEpisodes() })
    add(PlayerMenuEntry(s(R.string.player_parse), actions.onParse != null) { actions.onParse?.invoke() })
    add(
        PlayerMenuEntry(
            s(R.string.player_quality, PlaybackSession.qualityLabel),
            actions.onOpenQuality != null,
        ) { actions.onOpenQuality?.invoke() },
    )
    add(
        PlayerMenuEntry(
            s(R.string.player_line, PlaybackSession.flagName.ifBlank { defaultLabel }),
            actions.onSwitchLine != null,
        ) { actions.onSwitchLine?.invoke() },
    )
    add(PlayerMenuEntry("${PlaybackSession.speed}x", onLongClick = { PlaybackSession.toggleBoost() }) { PlaybackSession.cycleSpeed() })
    add(
        PlayerMenuEntry(
            if (PlaybackSession.repeatMode == Player.REPEAT_MODE_ONE) s(R.string.player_loop_on) else s(R.string.player_loop_off),
        ) { PlaybackSession.toggleRepeat() },
    )
    add(PlayerMenuEntry(s(R.string.player_track_panel), actions.onOpenTracks != null) { actions.onOpenTracks?.invoke() })
    add(PlayerMenuEntry(s(R.string.player_danmaku_settings), actions.onOpenDanmaku != null) { actions.onOpenDanmaku?.invoke() })
    add(PlayerMenuEntry(s(R.string.player_subtitle_search), actions.onSearchSubtitle != null) { actions.onSearchSubtitle?.invoke() })
    add(PlayerMenuEntry(s(R.string.player_subtitle_import), actions.onImportSubtitle != null) { actions.onImportSubtitle?.invoke() })
    add(PlayerMenuEntry(s(R.string.player_cast)) { actions.onDlna() })
    add(PlayerMenuEntry(s(R.string.player_pip)) { actions.onPip() })
    add(PlayerMenuEntry(s(R.string.player_resize, PlaybackSession.resizeLabel)) { PlaybackSession.cycleResizeMode() })
    add(PlayerMenuEntry(s(R.string.player_engine, if (PlaybackSession.engineMpv) "MPV" else "Exo")) {
        PlaybackSession.setEngine(!PlaybackSession.engineMpv)
    })
    add(PlayerMenuEntry(s(R.string.player_decode, PlaybackSession.decodeLabel)) { PlaybackSession.toggleDecode() })
    add(PlayerMenuEntry(s(R.string.player_render, PlaybackSession.renderLabel)) { PlaybackSession.toggleRenderMode() })
    add(PlayerMenuEntry(s(R.string.player_subtitle, PlaybackSession.subtitleLabel.ifBlank { defaultLabel })) { PlaybackSession.cycleSubtitle() })
    add(PlayerMenuEntry(s(R.string.player_subtitle_size, PlaybackSession.subtitleScale)) {
        PlaybackSession.cycleSubtitleScale()
    })
    add(PlayerMenuEntry(s(R.string.player_subtitle_pos, PlaybackSession.subtitlePosition.toInt())) {
        PlaybackSession.cycleSubtitlePosition()
    })
    add(PlayerMenuEntry(s(R.string.player_subtitle_color, PlaybackSession.subtitleColor)) {
        PlaybackSession.cycleSubtitleColor()
    })
    add(PlayerMenuEntry(s(R.string.player_audio_track, PlaybackSession.audioLabel.ifBlank { defaultLabel })) { PlaybackSession.cycleAudio() })
    add(PlayerMenuEntry(s(R.string.player_video_track, PlaybackSession.videoLabel.ifBlank { defaultLabel })) { PlaybackSession.cycleVideo() })
    add(PlayerMenuEntry(s(R.string.player_subtitle_offset, PlaybackSession.textOffsetMs)) { PlaybackSession.shiftTextOffset(100L) })
    add(PlayerMenuEntry(s(R.string.player_av_offset, PlaybackSession.audioOffsetMs)) { PlaybackSession.shiftAudioOffset(100L) })
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
