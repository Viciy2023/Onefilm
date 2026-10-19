package androidx.media3.exoplayer.text;

import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;

import java.util.List;

/** OneFilm compatibility shim. Secondary subtitle output is disabled in Phase 1. */
public final class SecondaryTextOutput implements TextOutput {

    @Override
    public void onCues(List<Cue> cues) {
    }

    @Override
    public void onCues(CueGroup cueGroup) {
    }
}
