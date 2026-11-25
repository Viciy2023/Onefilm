package com.onefilm.android.tv.compose.feature.detail

import com.onefilm.android.tv.compose.bridge.DetailDataSource
import com.onefilm.android.tv.compose.bridge.DetailInfo
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

class DetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val info = DetailInfo(
        key = "local",
        id = "1",
        title = "A",
        posterUrl = null,
        description = null,
        year = null,
        area = null,
        remarks = null,
        flags = emptyList(),
    )

    private class FakeDetailDataSource(
        private val info: DetailInfo? = null,
        private val error: Throwable? = null,
    ) : DetailDataSource {
        override suspend fun load(key: String, id: String): DetailInfo {
            error?.let { throw it }
            return info ?: error("missing")
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
        val viewModel = DetailViewModel("local", "1", FakeDetailDataSource(info))
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(DetailUiState.Content(info), viewModel.state.value)
    }

    @Test
    fun load_failure_emitsError() = runTest(dispatcher) {
        val viewModel = DetailViewModel("local", "1", FakeDetailDataSource(error = IOException("boom")))
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is DetailUiState.Error)
    }
}
