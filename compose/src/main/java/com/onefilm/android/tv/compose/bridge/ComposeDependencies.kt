package com.onefilm.android.tv.compose.bridge

object ComposeDependencies {
    lateinit var homeDataSource: HomeDataSource
    lateinit var detailDataSource: DetailDataSource
    lateinit var categoryDataSource: CategoryDataSource
    lateinit var searchDataSource: SearchDataSource
    lateinit var searchMetaDataSource: SearchMetaDataSource
    lateinit var libraryDataSource: LibraryDataSource
    lateinit var favoriteStore: FavoriteStore
    lateinit var sourceDataSource: SourceDataSource
    lateinit var detailLauncher: DetailLauncher
    lateinit var playbackResolver: PlaybackResolver
    lateinit var playbackHistoryStore: PlaybackHistoryStore
}
