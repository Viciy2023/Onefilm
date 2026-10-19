package androidx.media3.mpvplayer;

import java.io.File;

/** OneFilm compatibility shim. */
public final class MpvAndroidOptions {

    private MpvAndroidOptions(Builder builder) {
    }

    public static final class Builder {
        public Builder setShaderCacheDirectory(File directory) { return this; }
        public Builder setAudioPassthroughEnabled(boolean enabled) { return this; }
        public Builder setDolbyVisionOutputPolicy(int policy) { return this; }
        public Builder setVulkanEnabled(boolean enabled) { return this; }
        public Builder setGpuNextEnabled(boolean enabled) { return this; }
        public MpvAndroidOptions build() { return new MpvAndroidOptions(this); }
    }
}
