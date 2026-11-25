package com.onefilm.android.tv.aisubtitle;

import androidx.annotation.NonNull;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;

/**
 * LITE 版功能桩：纯直通，不做任何 PCM 抽头。
 *
 * <p>LITE 侧 {@link AiSubtitleEngine#prepare} 恒为 false，因此本 processor 不会被挂到
 * 音频链路；即使被挂上，这里的实现也只是原样下传，不会影响播放。
 */
public final class AiPcmTapProcessor extends BaseAudioProcessor {

    @NonNull
    @Override
    public AudioProcessor.AudioFormat onConfigure(@NonNull AudioProcessor.AudioFormat inputAudioFormat) {
        return inputAudioFormat;
    }

    @Override
    public void queueInput(@NonNull ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining <= 0) return;
        ByteBuffer out = replaceOutputBuffer(remaining);
        out.put(inputBuffer);
        out.flip();
    }
}
