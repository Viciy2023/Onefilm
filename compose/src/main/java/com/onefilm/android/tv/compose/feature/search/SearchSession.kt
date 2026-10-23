package com.onefilm.android.tv.compose.feature.search

import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.bridge.SearchProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 进程内搜索会话：搜索结果页进入详情后继续在后台跑，
 * 返回时复用同一批结果和进度；离开搜索相关页面才取消。
 */
object SearchSession {
    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dataSource: SearchDataSource? = null
    private var job: Job? = null
    private var lastKeyword: String = ""

    fun start(source: SearchDataSource, keyword: String) {
        dataSource = source
        val keywordTrimmed = keyword.trim()
        job?.cancel()
        lastKeyword = keywordTrimmed
        if (keywordTrimmed.isEmpty()) {
            _state.value = SearchUiState.Idle
            return
        }
        job = scope.launch {
            _state.value = SearchUiState.Loading
            var lastUpdate = 0L
            source.search(keywordTrimmed).collect { progress ->
                // 聚合搜索每命中一个站点都会回调；这里按 180ms 合并，
                // 避免 400+ 个站点完成时每一帧都重建整张卡片列表。
                val now = System.currentTimeMillis()
                if (!progress.done && now - lastUpdate < 250L) return@collect
                lastUpdate = now
                _state.value = when {
                    progress.items.isNotEmpty() -> SearchUiState.Content(
                        keywordTrimmed,
                        progress.items,
                        progress.done,
                        progress.completed,
                        progress.total,
                    )
                    progress.done -> SearchUiState.Empty(keywordTrimmed)
                    else -> SearchUiState.Loading
                }
            }
        }
    }

    fun retry(source: SearchDataSource) {
        if (lastKeyword.isNotEmpty()) start(source, lastKeyword)
    }

    /** 同一关键词的会话已存在时，返回结果页不应重新搜索。 */
    fun hasSession(keyword: String): Boolean =
        lastKeyword.isNotEmpty() && lastKeyword == keyword.trim() && _state.value != SearchUiState.Idle

    /** 离开搜索/详情/来源/播放器时结束会话。 */
    fun cancel() {
        job?.cancel()
        job = null
        lastKeyword = ""
        _state.value = SearchUiState.Idle
    }
}
