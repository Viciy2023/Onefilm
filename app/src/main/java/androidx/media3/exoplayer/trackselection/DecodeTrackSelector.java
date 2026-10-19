package androidx.media3.exoplayer.trackselection;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

/** OneFilm compatibility shim. Decode preference is ignored in Phase 1. */
public class DecodeTrackSelector extends DefaultTrackSelector {

    public DecodeTrackSelector(Context context) {
        super(context);
    }

    public void setRendererDecodePreferences(int audioDecode, int videoDecode) {
    }
}
