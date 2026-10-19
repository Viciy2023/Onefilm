package com.onefilm.android.tv.player.effect.audio;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;

/**
 * OneFilm: delays or advances PCM audio to compensate A/V sync.
 * Positive delay inserts silence, negative delay drops leading samples.
 * Non-PCM input is passed through unchanged.
 */
public final class AudioDelayProcessor extends BaseAudioProcessor {

    private static volatile long delayMs;

    private boolean enabled;
    private int frameSize = 4;
    private int sampleRate = 48000;
    private long pendingSilenceBytes;
    private long pendingDropBytes;
    private boolean pendingInit = true;
    private byte[] scratch = new byte[0];

    public static void setDelayMs(long value) {
        delayMs = value;
    }

    public static long getDelayMs() {
        return delayMs;
    }

    @NonNull
    @Override
    public AudioFormat onConfigure(@NonNull AudioFormat inputAudioFormat) {
        enabled = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT;
        sampleRate = inputAudioFormat.sampleRate;
        frameSize = Math.max(1, inputAudioFormat.channelCount) * 2;
        pendingInit = true;
        return inputAudioFormat;
    }

    @Override
    public void queueInput(@NonNull ByteBuffer inputBuffer) {
        if (!enabled) {
            copyInputToOutput(inputBuffer);
            return;
        }

        if (pendingInit) {
            pendingInit = false;
            long targetBytes = Math.abs(delayMs) * sampleRate / 1000 * frameSize;
            if (delayMs > 0) pendingSilenceBytes = targetBytes;
            else if (delayMs < 0) pendingDropBytes = targetBytes;
        }

        if (pendingSilenceBytes > 0) {
            int bytes = (int) Math.min(pendingSilenceBytes, 4096);
            bytes -= bytes % frameSize;
            if (bytes <= 0) bytes = frameSize;
            ByteBuffer out = replaceOutputBuffer(bytes);
            for (int i = 0; i < bytes; i++) out.put((byte) 0);
            out.flip();
            pendingSilenceBytes -= bytes;
            return;
        }

        if (pendingDropBytes > 0) {
            int drop = (int) Math.min(pendingDropBytes, inputBuffer.remaining());
            drop -= drop % frameSize;
            if (drop > 0) inputBuffer.position(inputBuffer.position() + drop);
            pendingDropBytes -= drop;
            if (!inputBuffer.hasRemaining()) return;
        }

        copyInputToOutput(inputBuffer);
    }

    /**
     * Copies the input through a scratch array. media3 can hand back this processor's own output
     * buffer as the next input buffer, so writing directly with {@code output.put(input)} would
     * throw "The source buffer is this buffer".
     */
    private void copyInputToOutput(@NonNull ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining <= 0) return;
        if (scratch.length < remaining) scratch = new byte[remaining];
        inputBuffer.get(scratch, 0, remaining);
        ByteBuffer out = replaceOutputBuffer(remaining);
        out.put(scratch, 0, remaining);
        out.flip();
    }

    @Override
    protected void onFlush() {
        pendingSilenceBytes = 0;
        pendingDropBytes = 0;
        pendingInit = true;
    }

    @Override
    protected void onReset() {
        pendingSilenceBytes = 0;
        pendingDropBytes = 0;
        pendingInit = true;
        enabled = false;
    }
}
