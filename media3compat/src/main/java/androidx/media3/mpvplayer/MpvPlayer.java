package androidx.media3.mpvplayer;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.mpvplayer.audio.MpvAudioFilter;
import androidx.media3.mpvplayer.video.MpvVideoEqualizer;

import java.util.List;

/**
 * OneFilm compatibility shim. Phase 1: MPV is unavailable, so this type only
 * satisfies compilation and is never instantiated (isAvailable() = false).
 */
public class MpvPlayer extends ForwardingPlayer {

    public static final int VIDEO_EFFECTS_SUPPORTED = 0;
    public static final int VIDEO_EFFECTS_UNSUPPORTED = 1;
    public static final int VIDEO_EFFECTS_UNSUPPORTED_DIRECT_DOLBY_VISION_OUTPUT = 2;

    public static final int AUDIO_EFFECTS_SUPPORTED = 0;
    public static final int AUDIO_EFFECTS_UNSUPPORTED = 1;
    public static final int AUDIO_EFFECTS_UNSUPPORTED_PASSTHROUGH = 2;

    public interface AudioOutputListener {
        void onAudioOutput();
    }

    private MpvPlayer(@Nullable Player delegate) {
        super(delegate);
    }

    public static boolean isAvailable() {
        return false;
    }

    public void setAudioOutputListener(@Nullable AudioOutputListener listener) {
    }

    public int getAudioChannelCount() {
        return 2;
    }

    public boolean addSubtitle(MediaItem.SubtitleConfiguration configuration) {
        return false;
    }

    public void setDecode(int decode) {
    }

    @Nullable
    public TrackSelectionOverride getPrimaryTextTrackSelectionOverride() {
        return null;
    }

    @Nullable
    public TrackSelectionOverride getSecondaryTextTrackSelectionOverride() {
        return null;
    }

    public List<TrackSelectionOverride> getSecondaryTextTrackSelectionOverrides() {
        return List.of();
    }

    public boolean isSecondaryTextTrackSuppressed() {
        return false;
    }

    public void setSecondaryTextTrackSelectionOverride(@Nullable TrackSelectionOverride override) {
    }

    public void resetSecondaryTextTrackSelection() {
    }

    public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {
    }

    public int getVideoEffectsSupport() {
        return VIDEO_EFFECTS_UNSUPPORTED;
    }

    public boolean isVideoSharpnessSupported() {
        return false;
    }

    public int getAudioEffectsSupport() {
        return AUDIO_EFFECTS_UNSUPPORTED;
    }

    public boolean setAudioFilter(MpvAudioFilter filter) {
        return false;
    }

    public void setVideoEqualizer(MpvVideoEqualizer equalizer) {
    }

    public void setSubtitleOptions(MpvSubtitleOptions options) {
    }

    public static final class Builder {
        private final Context context;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setDecode(int decode) {
            return this;
        }

        public Builder setConfig(MpvPlayerConfig config) {
            return this;
        }

        public MpvPlayer build() {
            return new MpvPlayer(null);
        }
    }
}
