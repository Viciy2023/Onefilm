package androidx.media3.exoplayer.libass;

import java.io.File;

/** OneFilm compatibility shim. Font family inspection is not implemented in Phase 1. */
public final class LibassFontFile {

    private LibassFontFile() {
    }

    public static String getFamilyName(File file) throws java.io.IOException {
        if (file == null) return null;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
