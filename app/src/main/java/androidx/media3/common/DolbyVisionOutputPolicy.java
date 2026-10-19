package androidx.media3.common;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** OneFilm compatibility shim. Placeholder for FongMi's media3 fork type. */
public final class DolbyVisionOutputPolicy {

    public static final int AUTO = 0;
    public static final int PREFER_TUNNELED = 1;
    public static final int PREFER_NON_TUNNELED = 2;
    public static final int ASSUME_SUPPORTED = 3;
    public static final int ASSUME_UNSUPPORTED = 4;

    @IntDef({AUTO, PREFER_TUNNELED, PREFER_NON_TUNNELED, ASSUME_SUPPORTED, ASSUME_UNSUPPORTED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    private DolbyVisionOutputPolicy() {
    }
}
