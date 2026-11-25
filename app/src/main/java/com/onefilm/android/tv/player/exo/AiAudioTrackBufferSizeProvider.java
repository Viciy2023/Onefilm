package com.onefilm.android.tv.player.exo;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

/**
 * 把 AudioTrack 缓冲放大，给 AI 字幕的识别器留出「已解码但尚未播出」的 PCM 预读量。
 *
 * <p>移植自上游 `AiAudioTrackBufferSizeProvider`（同一套 media3 用法）：
 * AudioTrack 容量开得足够大，ExoPlayer 才会持续预读解码，PCM 抽头拿到的音频
 * 才能领先实际播放若干秒；识别 + 翻译的全部耗时都在这段预读里完成，
 * 字幕才有可能与语音同步出现。缓冲不开大时，延迟必然 ≥（ASR + 翻译）耗时。
 *
 * <p>注意：这里只放大容量，不设置起始填充，因此不会拖慢起播。
 */
@UnstableApi
public final class AiAudioTrackBufferSizeProvider implements DefaultAudioSink.AudioTrackBufferSizeProvider {

    /**
     * 目标预读时长。
     *
     * <p>上游用 10s（要给 Whisper + 翻译留足预算）；我们当前只跑识别（RTF≈0.2），
     * 6s 足够，同时把「识别完成 → 字幕上屏」的等待从 5~8s 压到 1~3s。
     */
    public static final int LOOKAHEAD_MS = 6_000;

    private final DefaultAudioSink.AudioTrackBufferSizeProvider delegate;

    public AiAudioTrackBufferSizeProvider() {
        this(DefaultAudioSink.AudioTrackBufferSizeProvider.DEFAULT);
    }

    AiAudioTrackBufferSizeProvider(DefaultAudioSink.AudioTrackBufferSizeProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getBufferSizeInBytes(int minBufferSizeInBytes, int encoding, int outputMode,
                                    int pcmFrameSize, int sampleRate, int bitrate,
                                    double maxAudioTrackPlaybackSpeed) {
        int normal = delegate.getBufferSizeInBytes(minBufferSizeInBytes, encoding, outputMode,
                pcmFrameSize, sampleRate, bitrate, maxAudioTrackPlaybackSpeed);
        if (!Util.isEncodingLinearPcm(encoding) || pcmFrameSize <= 0 || sampleRate <= 0) return normal;
        int lookahead = lookaheadBytes(sampleRate, pcmFrameSize, LOOKAHEAD_MS);
        int result = Math.max(normal, lookahead);
        // 诊断：确认 provider 真的被 AudioSink 采用，以及最终容量对应多少秒
        long bytesPerSecond = (long) sampleRate * pcmFrameSize;
        android.util.Log.i("AiSubtitle", "audioTrack buffer: normal=" + normal + " lookahead=" + lookahead
                + " chosen=" + result + " seconds=" + String.format(java.util.Locale.US, "%.1f",
                bytesPerSecond <= 0 ? 0f : result / (float) bytesPerSecond));
        return result;
    }

    static int lookaheadBytes(int sampleRate, int pcmFrameSize, int durationMs) {
        if (sampleRate <= 0 || pcmFrameSize <= 0 || durationMs <= 0) return 0;
        long requested = (long) sampleRate * pcmFrameSize * durationMs / 1_000L;
        long maxAligned = Integer.MAX_VALUE - (Integer.MAX_VALUE % pcmFrameSize);
        long bounded = Math.min(requested, maxAligned);
        return (int) ((bounded / pcmFrameSize) * pcmFrameSize);
    }
}
