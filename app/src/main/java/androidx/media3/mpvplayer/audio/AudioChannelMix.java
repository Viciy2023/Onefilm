package androidx.media3.mpvplayer.audio;

/** OneFilm compatibility shim. Pure helpers mirrored from FongMi's API. */
public final class AudioChannelMix {

    private AudioChannelMix() {
    }

    public static float mixMono(float[] samples) {
        if (samples == null || samples.length == 0) return 0f;
        float sum = 0f;
        for (float s : samples) sum += s;
        return sum / samples.length;
    }

    public static float mixStereoLeft(float[] samples) {
        return mixMono(samples);
    }

    public static float mixStereoRight(float[] samples) {
        return mixMono(samples);
    }

    public static float[][] createFrontCenterGainMix(int channelCount, float gain) {
        return identity(channelCount);
    }

    public static float[][] createStereoMix(int channelCount, boolean reverse) {
        return identity(channelCount);
    }

    public static float[][] createMonoMix(int channelCount) {
        return identity(channelCount);
    }

    public static float[][] createFrontBalanceMix(int channelCount, float balance) {
        return identity(channelCount);
    }

    public static float[][] compose(float[][] a, float[][] b) {
        return a;
    }

    private static float[][] identity(int channelCount) {
        if (channelCount <= 0) return new float[0][0];
        float[][] mix = new float[channelCount][channelCount];
        for (int i = 0; i < channelCount; i++) mix[i][i] = 1f;
        return mix;
    }
}
