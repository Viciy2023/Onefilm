package androidx.media3.exoplayer.source.preload;

import androidx.annotation.NonNull;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.source.MediaSource;

/** OneFilm 兼容层：对应播放器扩展中的预加载 Supplier。 */
public interface MediaSourceFactorySupplier {

    @NonNull
    MediaSourceFactorySupplier setCache(Cache cache);

    @NonNull
    MediaSourceFactorySupplier setDataSourceFactory(DataSource.Factory dataSourceFactory);

    @NonNull
    MediaSource.Factory get();
}
