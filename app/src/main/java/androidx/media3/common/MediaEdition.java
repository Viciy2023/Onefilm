package androidx.media3.common;

/** OneFilm compatibility shim. Placeholder for FongMi's media3 fork type. */
public final class MediaEdition {
    public final String label;
    public final long durationUs;
    public boolean selected;

    public MediaEdition(String label, long durationUs) {
        this.label = label;
        this.durationUs = durationUs;
    }
}
