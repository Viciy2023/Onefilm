package com.onefilm.android.tv.player.exo;

import android.media.AudioTrack;

import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

import java.lang.reflect.Field;

/**
 * 读取 AudioTrack 的真实播放头（已播放毫秒数）。
 *
 * <p>为什么不用 `DefaultAudioSink.getCurrentPositionUs()`：实测它在本项目的 media3 fork 里
 * 返回的是「系统开机时长」量级的值（≈1.0e12 us），不是 AudioTrack 位置，不可用。
 *
 * <p>为什么不用上游的 `AiAudioOutputProvider`：它依赖 media3 1.6+ 的
 * `AudioOutputStream`/`AudioOutputProvider`，本项目的 fork 基线（1.8.0 产物但已 R8 裁剪）
 * 里没有该 API。
 *
 * <p>因此直接读 `DefaultAudioSink.audioTrack`（字段名已用 javap 确认为原名、未被混淆），
 * 调 `AudioTrack.getPlaybackHeadPosition()`。它与 PCM 抽头的累计写入量同基准
 * （AudioTrack 创建时为 0、seek 后重置），所以 `lookahead = 写入毫秒 − 已播放毫秒`。
 *
 * <p>反射失败时返回 -1，调用方按「无法测量」处理（不崩、不阻塞播放）。
 */
public final class AiAudioClock {

    /** 只保留主播放器的 AudioSink；预加载器构建的 sink 不参与。 */
    private static volatile AudioSink sink;

    private static volatile Field audioTrackField;
    private static volatile boolean fieldResolved;

    private AiAudioClock() {
    }

    public static void attach(AudioSink audioSink) {
        sink = audioSink;
    }

    /** AudioTrack 已播放的毫秒数；不可用时返回 -1。 */
    public static long playedMs() {
        AudioSink current = sink;
        if (current == null) return -1L;
        try {
            Field field = resolveField();
            if (field == null) return -1L;
            Object track = field.get(current);
            if (!(track instanceof AudioTrack)) return -1L;
            AudioTrack audioTrack = (AudioTrack) track;
            int frames = audioTrack.getPlaybackHeadPosition();
            int sampleRate = audioTrack.getSampleRate();
            if (frames < 0 || sampleRate <= 0) return -1L;
            long ms = frames * 1000L / sampleRate;
            // 诊断：确认拿到的是正在播放的 AudioTrack（state=3 表示 PLAYSTATE_PLAYING），
            // 以及帧数/采样率换算是否合理。
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastLogAtMs > 5000L) {
                lastLogAtMs = now;
                android.util.Log.i("AiSubtitle", "audioClock state=" + audioTrack.getState()
                        + " rate=" + sampleRate + " head=" + frames + " playedMs=" + ms
                        + " sink=" + current.getClass().getSimpleName());
            }
            return ms;
        } catch (Throwable t) {
            return -1L;
        }
    }

    private static volatile long lastLogAtMs;

    private static Field resolveField() {
        if (fieldResolved) return audioTrackField;
        synchronized (AiAudioClock.class) {
            if (!fieldResolved) {
                try {
                    Field field = DefaultAudioSink.class.getDeclaredField("audioTrack");
                    field.setAccessible(true);
                    audioTrackField = field;
                } catch (Throwable ignored) {
                    audioTrackField = null;
                }
                fieldResolved = true;
            }
        }
        return audioTrackField;
    }
}
