package androidx.media3.mpvplayer.audio;

/** OneFilm compatibility shim. Filter description accepted by the MPV engine stub. */
public final class MpvAudioFilter {

    public static final MpvAudioFilter EMPTY = new Builder().build();

    private MpvAudioFilter(Builder builder) {
    }

    public static final class Builder {
        public Builder addLoudnessNormalization(String id, double target, double range, double offset) { return this; }
        public Builder addCompressor(String id, float threshold, float ratio, float makeup, float mix) { return this; }
        public Builder addVolume(String id, double volume) { return this; }
        public Builder addEqualizer(String id, double frequencyKhz, double gain) { return this; }
        public Builder addLimiter(String id, double level) { return this; }
        public Builder addRuntimeChannelMix(String id, float[][] mix) { return this; }
        public MpvAudioFilter build() { return new MpvAudioFilter(this); }
    }
}
