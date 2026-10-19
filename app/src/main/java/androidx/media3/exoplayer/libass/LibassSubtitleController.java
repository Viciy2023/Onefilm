package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.text.SecondaryTextOutput;
import androidx.media3.exoplayer.trackselection.SecondaryTextTrackSelector;

import java.util.List;

/** OneFilm compatibility shim. libass is disabled in Phase 1. */
public final class LibassSubtitleController {

    public LibassSubtitleController(ExoPlayer player, LibassPlaybackSession session,
                                    SecondaryTextTrackSelector.Factory selectorFactory,
                                    SecondaryTextOutput output) {
    }

    public void close() {
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

    public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {
    }
}
