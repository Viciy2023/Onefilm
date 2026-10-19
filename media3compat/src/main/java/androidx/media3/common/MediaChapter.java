package androidx.media3.common;

/** OneFilm compatibility shim. Placeholder for FongMi's media3 fork type. */
public final class MediaChapter {
    public final String label;
    public final long timeUs;
    public boolean selected;

    public MediaChapter(String label, long timeUs) {
        this.label = label;
        this.timeUs = timeUs;
    }
}
