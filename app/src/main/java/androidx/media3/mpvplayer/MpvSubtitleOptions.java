package androidx.media3.mpvplayer;

/** OneFilm compatibility shim. */
public final class MpvSubtitleOptions {

    private MpvSubtitleOptions(Builder builder) {
    }

    public static final class Builder {
        public Builder setPosition(double position) { return this; }
        public Builder setScale(float scale) { return this; }
        public Builder setSecondarySubtitlePosition(double position) { return this; }
        public Builder setSecondaryAssStyleOverride(boolean enabled) { return this; }
        public Builder setFontFamily(String family) { return this; }
        public Builder setFontsDirectory(String directory) { return this; }
        public Builder setCustomStyle(int textColor, int backgroundColor, int edgeType, int edgeColor, float edgeWidth, float shadow) { return this; }
        public Builder setSystemCaptionStyle() { return this; }
        public MpvSubtitleOptions build() { return new MpvSubtitleOptions(this); }
    }
}
