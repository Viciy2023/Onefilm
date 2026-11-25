package androidx.media3.ui.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.libass.LibassPlaybackSession;
import androidx.media3.exoplayer.libass.LibassSubtitleController;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import java.util.function.Consumer;

/** OneFilm compatibility shim. libass rendering is disabled in Phase 1. */
public final class LibassPlayerViewController {

    public LibassPlayerViewController(Player player, LibassPlaybackSession session,
                                      LibassSubtitleController controller) {
    }

    public void bind(PlayerView playerView) {
    }

    public void setStyleOverride(@Nullable CaptionStyleCompat style, @Nullable String fontFamily) {
    }

    public void setSecondarySubtitleViewConfigurator(Consumer<SubtitleView> configurator) {
    }

    public void close() {
    }
}
