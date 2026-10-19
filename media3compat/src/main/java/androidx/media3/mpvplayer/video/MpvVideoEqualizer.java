package androidx.media3.mpvplayer.video;

/** OneFilm compatibility shim. */
public final class MpvVideoEqualizer {

    public static final MpvVideoEqualizer DEFAULT = new MpvVideoEqualizer(0f, 0f, 0f, 0f, 0f, 0f);

    public final float brightness;
    public final float contrast;
    public final float saturation;
    public final float gamma;
    public final float hue;
    public final float sharpness;

    private MpvVideoEqualizer(float brightness, float contrast, float saturation, float gamma, float hue, float sharpness) {
        this.brightness = brightness;
        this.contrast = contrast;
        this.saturation = saturation;
        this.gamma = gamma;
        this.hue = hue;
        this.sharpness = sharpness;
    }

    public static MpvVideoEqualizer create(float brightness, float contrast, float saturation, float gamma, float hue, float sharpness) {
        return new MpvVideoEqualizer(brightness, contrast, saturation, gamma, hue, sharpness);
    }
}
