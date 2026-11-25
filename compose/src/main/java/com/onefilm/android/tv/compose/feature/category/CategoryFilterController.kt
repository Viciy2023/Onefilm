package com.onefilm.android.tv.compose.feature.category

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 分类页内部事件：再次点击当前分类标签时，请求打开底部筛选层。
 * 只保存当前页面 key/tid，不参与数据加载。
 */
object CategoryFilterController {

    val categoryFocusRequester = FocusRequester()

    private var currentKey: String? = null
    private var currentTid: String? = null

    private val _show = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val show: SharedFlow<Unit> = _show

    /** 筛选数据版本号：分类页拿到筛选条件后自增，驱动标签上的漏斗图标刷新。 */
    var filtersVersion by mutableIntStateOf(0)
        private set

    fun refreshFilters() {
        filtersVersion++
    }

    fun bind(key: String, tid: String) {
        currentKey = key
        currentTid = tid
    }

    fun unbind(key: String, tid: String) {
        if (currentKey == key && currentTid == tid) {
            currentKey = null
            currentTid = null
        }
    }

    fun matches(key: String, tid: String): Boolean =
        currentKey == key && currentTid == tid

    fun requestShow() {
        _show.tryEmit(Unit)
    }
}
