package androidx.media3.exoplayer.trackselection;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

/** OneFilm compatibility shim. */
public class SecondaryTextTrackSelector extends DefaultTrackSelector {

    public SecondaryTextTrackSelector(Context context) {
        super(context);
    }

    public static final class Factory implements TrackSelector.Factory {

        @SuppressWarnings("unused")
        private final TrackSelector.Factory delegate;

        public Factory(TrackSelector.Factory delegate) {
            this.delegate = delegate;
        }

        @NonNull
        @Override
        public TrackSelector createTrackSelector(@NonNull Context context) {
            return new SecondaryTextTrackSelector(context);
        }
    }
}
