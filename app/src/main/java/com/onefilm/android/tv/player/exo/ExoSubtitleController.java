package com.onefilm.android.tv.player.exo;

import androidx.annotation.Nullable;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.libass.LibassPlaybackSession;
import androidx.media3.exoplayer.libass.LibassSubtitleController;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.media3.ui.libass.LibassPlayerViewController;

import com.onefilm.android.tv.player.engine.PlayerEngine.SecondarySubtitleState;
import com.onefilm.android.tv.setting.SubtitleSetting;
import io.github.assrender.SubtitleOverlayView;

final class ExoSubtitleController {

    private final LibassSubtitleController libassSubtitleController;
    private final LibassPlaybackSession libassPlaybackSession;
    private final LibassPlayerViewController playerViewController;
    private final SubtitleOverlayView assOverlay;

    ExoSubtitleController(ExoPlayerSession session) {
        libassPlaybackSession = session.libassPlaybackSession();
        libassSubtitleController = session.libassSubtitleController();
        playerViewController = new LibassPlayerViewController(session.player(), libassPlaybackSession, libassSubtitleController);
        this.assOverlay = session.assOverlay();
        applySubtitleStyle();
        applySecondarySubtitleMode();
    }

    void release() {
        playerViewController.close();
    }

    void bindPlayerView(PlayerView playerView) {
        playerViewController.bind(playerView);
        if (assOverlay != null && assOverlay.getParent() == null) playerView.addView(assOverlay, new PlayerView.LayoutParams(PlayerView.LayoutParams.MATCH_PARENT, PlayerView.LayoutParams.MATCH_PARENT));
    }

    void applySubtitleStyle() {
        libassPlaybackSession.setBottomPositionFraction(SubtitleSetting.getPosition() / 100.0f);
        libassPlaybackSession.setSecondaryBottomPositionFraction(getSecondaryBottomPositionFraction());
        libassPlaybackSession.setFontScale(SubtitleSetting.isScaleApplied() ? SubtitleSetting.getAppliedScale() : 1.0f, true);
        playerViewController.setStyleOverride(SubtitleSetting.isStyleForced() ? SubtitleSetting.getStyle() : null, SubtitleSetting.getFontFamily());
        playerViewController.setSecondarySubtitleViewConfigurator(this::applySecondarySubtitleStyle);
    }

    SecondarySubtitleState getSecondarySubtitleState() {
        return new SecondarySubtitleState(libassSubtitleController.getPrimaryTextTrackSelectionOverride(), libassSubtitleController.getSecondaryTextTrackSelectionOverride(), libassSubtitleController.getSecondaryTextTrackSelectionOverrides(), libassSubtitleController.isSecondaryTextTrackSuppressed());
    }

    void setSecondarySubtitleSelection(@Nullable TrackSelectionOverride selection) {
        applySecondarySubtitleMode();
        libassSubtitleController.setSecondaryTextTrackSelectionOverride(selection);
    }

    private void applySecondarySubtitleMode() {
        libassSubtitleController.setSecondaryTextTrackAutoSelectionEnabled(SubtitleSetting.getSecondaryMode() == SubtitleSetting.SECONDARY_MODE_AUTO);
    }

    private void applySecondarySubtitleStyle(SubtitleView subtitleView) {
        SubtitleSetting.applyStyle(subtitleView);
        subtitleView.setBottomPaddingFraction(getSecondaryBottomPositionFraction());
    }

    private static float getSecondaryBottomPositionFraction() {
        return (100.0f - SubtitleSetting.getSecondaryPosition()) / 100.0f;
    }
}
