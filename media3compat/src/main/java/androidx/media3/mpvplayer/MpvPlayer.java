package androidx.media3.mpvplayer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.mpvplayer.audio.MpvAudioFilter;
import androidx.media3.mpvplayer.video.MpvVideoEqualizer;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.List;

/**
 * OneFilm: media3 Player backed by the standalone MPV runtime APK.
 * Surface is passed through the bridge to libmpv; state is polled from the runtime.
 */
@UnstableApi
public class MpvPlayer extends SimpleBasePlayer {

    public static final int VIDEO_EFFECTS_SUPPORTED = 0;
    public static final int VIDEO_EFFECTS_UNSUPPORTED = 1;
    public static final int VIDEO_EFFECTS_UNSUPPORTED_DIRECT_DOLBY_VISION_OUTPUT = 2;

    public static final int AUDIO_EFFECTS_SUPPORTED = 0;
    public static final int AUDIO_EFFECTS_UNSUPPORTED = 1;
    public static final int AUDIO_EFFECTS_UNSUPPORTED_PASSTHROUGH = 2;

    public interface AudioOutputListener {
        void onAudioOutput();
    }

    /** Bridge implemented by the app, forwarding to MpvRuntimeService. */
    public interface RuntimeBridge {
        void setSurface(@Nullable Surface surface);

        void open(String url);

        void play();

        void pause();
        void stop();

        void setAudioDelayMs(long delayMs);

        void setSubtitleDelayMs(long delayMs);

        int getAudioTrackCount();

        int getSubtitleTrackCount();

        int getAudioTrack();

        int getSubtitleTrack();

        int getVideoWidth();

        int getVideoHeight();

        void setAudioTrack(int id);

        void setSubtitleTrack(int id);

        String[] getAudioTrackDetails();

        String[] getSubtitleTrackDetails();

        void seekTo(long positionMs);

        long getPosition();

        long getDuration();

        void release();
    }

    private static final long POSITION_POLL_MS = 500;

    private final RuntimeBridge bridge;
    private final Handler handler;

    private MediaItem mediaItem;
    private long positionMs;
    private long durationMs;
    private int playbackState = Player.STATE_IDLE;
    private boolean playWhenReady;
    private boolean released;

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            if (released) return;
            long nextPosition = bridge.getPosition();
            long nextDuration = bridge.getDuration();
            boolean changed = Math.abs(nextPosition - positionMs) > 250 || nextDuration != durationMs;
            positionMs = nextPosition;
            if (nextDuration > 0) durationMs = nextDuration;
            if (playbackState == Player.STATE_BUFFERING && durationMs > 0) playbackState = Player.STATE_READY;
            if (changed) invalidateState();
            handler.postDelayed(this, POSITION_POLL_MS);
        }
    };

    private MpvPlayer(Looper looper, RuntimeBridge bridge) {
        super(looper);
        this.bridge = bridge;
        this.handler = new Handler(looper);
        handler.postDelayed(poller, POSITION_POLL_MS);
    }

    public static boolean isAvailable() {
        return available;
    }

    private static volatile boolean available;

    @Override
    protected State getState() {
        MediaItem item = mediaItem == null ? MediaItem.EMPTY : mediaItem;
        long durationUs = durationMs * 1000L;
        SimpleBasePlayer.PeriodData period =
                new SimpleBasePlayer.PeriodData.Builder(item)
                        .setDurationUs(durationUs)
                        .build();
        SimpleBasePlayer.MediaItemData data =
                new SimpleBasePlayer.MediaItemData.Builder(item)
                        .setMediaItem(item)
                        .setDurationUs(durationUs)
                        .setIsSeekable(true)
                        .setTracks(buildTracks())
                        .setPeriods(Collections.singletonList(period))
                        .build();
        Player.Commands commands =
                new Player.Commands.Builder()
                        .addAll(
                                Player.COMMAND_PLAY_PAUSE,
                                Player.COMMAND_PREPARE,
                                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                                Player.COMMAND_GET_TIMELINE,
                                Player.COMMAND_SET_MEDIA_ITEM,
                                Player.COMMAND_SET_VIDEO_SURFACE,
                                Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS,
                                Player.COMMAND_STOP)
                        .build();
        return new State.Builder()
                .setAvailableCommands(commands)
                .setPlaybackState(playbackState)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setVideoSize(new VideoSize(bridge.getVideoWidth(), bridge.getVideoHeight()))
                .setPlaylist(Collections.singletonList(data))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(positionMs)
                .build();
    }

    private Tracks buildTracks() {
        java.util.List<Tracks.Group> groups = new java.util.ArrayList<>();
        int videoWidth = bridge.getVideoWidth();
        int videoHeight = bridge.getVideoHeight();
        if (videoWidth > 0 && videoHeight > 0) {
            androidx.media3.common.Format format = new androidx.media3.common.Format.Builder()
                    .setId("mpv-v-1")
                    .setSampleMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                    .setWidth(videoWidth)
                    .setHeight(videoHeight)
                    .build();
            androidx.media3.common.TrackGroup group =
                    new androidx.media3.common.TrackGroup("mpv-v-1", format);
            groups.add(new Tracks.Group(group, false, new int[] {androidx.media3.common.C.FORMAT_HANDLED}, new boolean[] {true}));
        }
        addTrackGroups(groups, bridge.getAudioTrackDetails(), "mpv-a-", androidx.media3.common.MimeTypes.AUDIO_AAC, bridge.getAudioTrack());
        addTrackGroups(groups, bridge.getSubtitleTrackDetails(), "mpv-s-", androidx.media3.common.MimeTypes.TEXT_SSA, bridge.getSubtitleTrack());
        return groups.isEmpty() ? Tracks.EMPTY : new Tracks(groups);
    }

    /**
     * OneFilm: media3 requires all formats in one TrackGroup to share the same language, so every
     * MPV track gets its own single-format group. This also makes the dialog row -&gt; track mapping
     * unambiguous.
     */
    private static void addTrackGroups(java.util.List<Tracks.Group> out, String[] details, String prefix, String mime, int currentId) {
        if (details == null) return;
        for (String detail : details) {
            String[] parts = detail.split("\t", -1);
            int id = parts.length > 0 ? parseInt(parts[0]) : 0;
            String lang = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
            String title = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
            androidx.media3.common.Format format = new androidx.media3.common.Format.Builder()
                    .setId(prefix + id)
                    .setLanguage(lang)
                    .setLabel(title)
                    .setSampleMimeType(mime)
                    .build();
            androidx.media3.common.TrackGroup group = new androidx.media3.common.TrackGroup(prefix + id, format);
            out.add(new Tracks.Group(group, false, new int[] {androidx.media3.common.C.FORMAT_HANDLED}, new boolean[] {id == currentId}));
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected ListenableFuture<?> handleSetTrackSelectionParameters(androidx.media3.common.TrackSelectionParameters parameters) {
        for (TrackSelectionOverride override : parameters.overrides.values()) {
            androidx.media3.common.TrackGroup group = override.mediaTrackGroup;
            String groupId = group.id;
            if (groupId == null) continue;
            boolean audioGroup = groupId.startsWith("mpv-a-");
            boolean textGroup = groupId.startsWith("mpv-s-");
            if (!audioGroup && !textGroup) continue;
            if (override.trackIndices.isEmpty()) {
                // Toggling the active track off means "disable this track type" in MPV.
                if (audioGroup) bridge.setAudioTrack(0);
                else bridge.setSubtitleTrack(0);
                continue;
            }
            int index = override.trackIndices.get(0);
            String prefix = audioGroup ? "mpv-a-" : "mpv-s-";
            String formatId = group.getFormat(index).id;
            int id = formatId != null && formatId.length() > prefix.length()
                    ? parseInt(formatId.substring(prefix.length())) : 0;
            android.util.Log.i("MpvTracks", "select group=" + groupId + " index=" + index + " id=" + id);
            if (audioGroup) {
                bridge.setAudioTrack(id);
            } else {
                bridge.setSubtitleTrack(id);
            }
        }
        if (parameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)) {
            bridge.setAudioTrack(0);
        }
        if (parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
            bridge.setSubtitleTrack(0);
        }
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
        this.playWhenReady = playWhenReady;
        if (playWhenReady) bridge.play();
        else bridge.pause();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        int index = startIndex >= 0 ? Math.min(startIndex, mediaItems.size() - 1) : 0;
        mediaItem = mediaItems.isEmpty() ? MediaItem.EMPTY : mediaItems.get(index);
        positionMs = startPositionMs;
        durationMs = 0;
        playbackState = Player.STATE_IDLE;
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handlePrepare() {
        playbackState = Player.STATE_BUFFERING;
        if (mediaItem != null && mediaItem.localConfiguration != null
                && mediaItem.localConfiguration.uri != null) {
            bridge.open(mediaItem.localConfiguration.uri.toString());
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        this.positionMs = positionMs;
        bridge.seekTo(positionMs);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
        if (videoOutput instanceof SurfaceView) {
            bridge.setSurface(((SurfaceView) videoOutput).getHolder().getSurface());
        } else if (videoOutput instanceof TextureView) {
            TextureView textureView = (TextureView) videoOutput;
            if (textureView.getSurfaceTexture() != null) {
                bridge.setSurface(new Surface(textureView.getSurfaceTexture()));
            }
        } else if (videoOutput instanceof Surface) {
            bridge.setSurface((Surface) videoOutput);
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
        bridge.setSurface(null);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        playbackState = Player.STATE_IDLE;
        playWhenReady = false;
        bridge.stop();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
        released = true;
        handler.removeCallbacks(poller);
        bridge.release();
        return Futures.immediateVoidFuture();
    }

    // ---- FongMi API compatibility (not implemented by the MPV runtime yet) ----

    public void setAudioOutputListener(@Nullable AudioOutputListener listener) {
    }

    public int getAudioChannelCount() {
        return 2;
    }

    public boolean addSubtitle(MediaItem.SubtitleConfiguration configuration) {
        return false;
    }

    /** OneFilm: manual surface attach for hosts that do not use PlayerView. */
    public void attachSurface(@Nullable Surface surface) {
        bridge.setSurface(surface);
    }

    public void setAudioOffsetMs(long delayMs) {
        bridge.setAudioDelayMs(delayMs);
    }

    public void setTextOffsetMs(long delayMs) {
        bridge.setSubtitleDelayMs(delayMs);
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
        return Collections.emptyList();
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
        private RuntimeBridge bridge;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setDecode(int decode) {
            return this;
        }

        public Builder setConfig(MpvPlayerConfig config) {
            return this;
        }

        public Builder setRuntimeBridge(@Nullable RuntimeBridge bridge) {
            this.bridge = bridge;
            return this;
        }

        public MpvPlayer build() {
            RuntimeBridge runtime = bridge == null ? new NoopBridge() : bridge;
            available = bridge != null;
            return new MpvPlayer(Looper.getMainLooper(), runtime);
        }
    }

    private static final class NoopBridge implements RuntimeBridge {
        @Override public void setSurface(@Nullable Surface surface) { }
        @Override public void open(String url) { }
        @Override public void play() { }
        @Override public void pause() { }
        @Override public void stop() { }
        @Override public void setAudioDelayMs(long delayMs) { }
        @Override public void setSubtitleDelayMs(long delayMs) { }
        @Override public int getAudioTrackCount() { return 0; }
        @Override public int getSubtitleTrackCount() { return 0; }
        @Override public int getAudioTrack() { return 0; }
        @Override public int getSubtitleTrack() { return 0; }
        @Override public int getVideoWidth() { return 0; }
        @Override public int getVideoHeight() { return 0; }
        @Override public void setAudioTrack(int id) { }
        @Override public void setSubtitleTrack(int id) { }
        @Override public String[] getAudioTrackDetails() { return new String[0]; }
        @Override public String[] getSubtitleTrackDetails() { return new String[0]; }
        @Override public void seekTo(long positionMs) { }
        @Override public long getPosition() { return 0; }
        @Override public long getDuration() { return 0; }
        @Override public void release() { }
    }
}
