package androidx.media3.exoplayer.source.preload;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;

/** OneFilm compatibility shim. Disk preloading is disabled in Phase 1. */
public final class DiskPreloadManager {

    private DiskPreloadManager(Builder builder) {
    }

    public void start(ExoPlayer player, MediaItem mediaItem, Options options) {
    }

    public void release() {
    }

    public static final class Builder {
        public Builder(Cache cache, DataSource.Factory upstreamFactory, RenderersFactory renderersFactory) {
        }

        public Builder setPriorityTaskManager(Object priorityTaskManager) {
            return this;
        }

        public DiskPreloadManager build() {
            return new DiskPreloadManager(this);
        }
    }

    public static final class Options {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            public Builder setDurationMs(long durationMs) { return this; }
            public Builder setMaxThreads(int maxThreads) { return this; }
            public Options build() { return new Options(); }
        }
    }
}
