package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.SubtitleParser;

/** OneFilm compatibility shim. libass is disabled in Phase 1. */
public final class LibassPlaybackSession {

    private final boolean enabled;

    public LibassPlaybackSession(LibassConfiguration configuration, boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAvailable() {
        return false;
    }

    public MediaComponents createMediaComponents(MediaItem mediaItem, ExtractorsFactory defaultFactory) {
        return new MediaComponents(defaultFactory, null);
    }

    public void setPreloadMediaItem(@Nullable MediaItem mediaItem) {
    }

    public boolean hasLibassSettingChanged() {
        return false;
    }

    @Nullable
    public Renderer createClockRenderer() {
        return null;
    }

    public void setBottomPositionFraction(float fraction) {
    }

    public void setSecondaryBottomPositionFraction(float fraction) {
    }

    public void setFontScale(float scale, boolean applyToSecondary) {
    }

    public void close() {
    }

    public static final class MediaComponents {
        public final ExtractorsFactory extractorsFactory;
        @Nullable
        public final SubtitleParser.Factory subtitleParserFactory;

        public MediaComponents(ExtractorsFactory extractorsFactory, @Nullable SubtitleParser.Factory subtitleParserFactory) {
            this.extractorsFactory = extractorsFactory;
            this.subtitleParserFactory = subtitleParserFactory;
        }
    }
}
