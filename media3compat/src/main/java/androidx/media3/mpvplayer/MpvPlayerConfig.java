package androidx.media3.mpvplayer;

import android.content.Context;

import java.io.File;

/** OneFilm compatibility shim. Collects MPV options; nothing is applied in Phase 1. */
public final class MpvPlayerConfig {

    private MpvPlayerConfig(Builder builder) {
    }

    public static final class Builder {
        public Builder addConfigDirectory(File directory) { return this; }
        public Builder addAndroidDefaults(MpvAndroidOptions options) { return this; }
        public Builder addTlsCaFileFromAsset(Context context, String assetName, File target) { return this; }
        public Builder addAndroidSubtitleOptions(Context context, MpvSubtitleOptions options) { return this; }
        public Builder addDiskCacheOptions(File directory, int seconds) { return this; }
        public MpvPlayerConfig build() { return new MpvPlayerConfig(this); }
    }
}
