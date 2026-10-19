package com.onefilm.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;

import org.junit.Test;

/** JVM tests for the media3 fork's OneFilm Dolby Vision output policy. */
public class DolbyVisionPolicyTest {

    @Test
    public void remapDolbyVisionFormat_usesHevcBaseLayerAndDropsDvCodecs() {
        Format dvFormat = new Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs("dvh.08.06")
                .setWidth(3840)
                .setHeight(2160)
                .build();

        Format remapped = MediaCodecVideoRenderer.remapDolbyVisionFormatForUnsupportedOutput(dvFormat);

        assertEquals(MimeTypes.VIDEO_H265, remapped.sampleMimeType);
        assertNull(remapped.codecs);
        assertEquals(3840, remapped.width);
        assertEquals(2160, remapped.height);
    }

    @Test
    public void remapNonDolbyVisionFormat_returnsSameInstance() {
        Format hevcFormat = new Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setCodecs("hvc1.2.4.L153.B0")
                .build();

        assertSame(hevcFormat, MediaCodecVideoRenderer.remapDolbyVisionFormatForUnsupportedOutput(hevcFormat));
    }

    @Test
    public void tunnelingPolicy_blocksNonTunneledAndUnsupportedModes() {
        assertTrue(MediaCodecVideoRenderer.shouldAllowDolbyVisionTunneling(MediaCodecVideoRenderer.DV_POLICY_AUTO));
        assertTrue(MediaCodecVideoRenderer.shouldAllowDolbyVisionTunneling(MediaCodecVideoRenderer.DV_POLICY_PREFER_TUNNELED));
        assertTrue(MediaCodecVideoRenderer.shouldAllowDolbyVisionTunneling(MediaCodecVideoRenderer.DV_POLICY_ASSUME_SUPPORTED));
        assertFalse(MediaCodecVideoRenderer.shouldAllowDolbyVisionTunneling(MediaCodecVideoRenderer.DV_POLICY_PREFER_NON_TUNNELED));
        assertFalse(MediaCodecVideoRenderer.shouldAllowDolbyVisionTunneling(MediaCodecVideoRenderer.DV_POLICY_ASSUME_UNSUPPORTED));
    }

    @Test
    public void alternativeDecoder_selectionHonoursExplicitPolicies() {
        // Explicit policies must not depend on the actual display capabilities.
        assertTrue(MediaCodecVideoRenderer.shouldUseAlternativeDecoderForDolbyVision(
                null, MediaCodecVideoRenderer.DV_POLICY_ASSUME_UNSUPPORTED));
        assertFalse(MediaCodecVideoRenderer.shouldUseAlternativeDecoderForDolbyVision(
                null, MediaCodecVideoRenderer.DV_POLICY_ASSUME_SUPPORTED));
    }
}
