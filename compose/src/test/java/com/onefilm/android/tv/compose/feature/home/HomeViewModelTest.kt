package com.onefilm.android.tv.compose.feature.home

import com.onefilm.android.tv.compose.bridge.HomeDataSource
import com.onefilm.android.tv.compose.bridge.HomeFeed
import com.onefilm.android.tv.compose.bridge.HomeSection
import com.onefilm.android.tv.compose.bridge.MediaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val feed = HomeFeed(
        hero = listOf(MediaCard("local", "1", "A", null, null)),
        sections = listOf(HomeSection("推荐", listOf(MediaCard("local", "1", "A", null, null)))),
    )

    private class FakeHomeDataSource(
        private val feed: HomeFeed? = null,
        private val error: Throwable? = null,
    ) : HomeDataSource {
        override suspend fun load(): HomeFeed {
            error?.let { throw it }
            return feed ?: HomeFeed(emptyList(), emptyList())
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_success_emitsContent() = runTest(dispatcher) {
        val viewModel = HomeViewModel(FakeHomeDataSource(feed))
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(HomeUiState.Content(feed), viewModel.state.value)
    }

    @Test
    fun load_empty_emitsEmpty() = runTest(dispatcher) {
        val viewModel = HomeViewModel(FakeHomeDataSource(HomeFeed(emptyList(), emptyList())))
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(HomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun load_failure_emitsError() = runTest(dispatcher) {
        val viewModel = HomeViewModel(FakeHomeDataSource(error = IOException("boom")))
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is HomeUiState.Error)
    }
}
