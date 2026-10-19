package androidx.media3.exoplayer.source.preload;

import androidx.annotation.NonNull;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.source.MediaSource;

/** OneFilm compatibility shim. Mirrors the FongMi fork's preload supplier. */
public interface MediaSourceFactorySupplier {

    @NonNull
    MediaSourceFactorySupplier setCache(Cache cache);

    @NonNull
    MediaSourceFactorySupplier setDataSourceFactory(DataSource.Factory dataSourceFactory);

    @NonNull
    MediaSource.Factory get();
}
