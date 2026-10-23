package com.onefilm.android.tv.compose.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.LibraryData
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.component.AppLogoLoading
import com.onefilm.android.tv.compose.design.component.TvPosterCard
import com.onefilm.android.tv.compose.navigation.TvBrandRow
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(
    data: LibraryData?,
    onCardClick: (MediaCard) -> Unit,
    initialTab: Int = 0,
    onDeleteFavorite: (MediaCard) -> Unit = {},
    onClearFavorites: () -> Unit = {},
    onDeleteHistory: (MediaCard) -> Unit = {},
    onClearHistory: () -> Unit = {},
) {
    val historyMode = initialTab == 1
    val items = if (historyMode) data?.history.orEmpty() else data?.favorites.orEmpty()
    var deleteMode by remember(historyMode) { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MediaCard?>(null) }
    var pendingClear by remember { mutableStateOf(false) }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }

    // 历史/收藏页没有导航行，进入页面或从详情返回时必须主动恢复卡片焦点，
    // 否则遥控器会停在“无焦点”状态，必须多按一次方向键才出现选中框。
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            delay(120)
            val target = lastFocusedKey?.takeIf { focusRequesters.containsKey(it) }
                ?: items.firstOrNull()?.let { "${it.key}:${it.id}" }
            target?.let { focusRequesters[it]?.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        TvBrandRow(
            compact = true,
            horizontalPadding = 0.dp,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (historyMode) "历史" else "收藏",
                fontSize = 20.sp,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (historyMode) {
                Button(
                    onClick = { deleteMode = !deleteMode },
                    enabled = items.isNotEmpty(),
                ) {
                    Text(if (deleteMode) "退出删除" else "删除记录", fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { pendingClear = true },
                    enabled = items.isNotEmpty(),
                ) {
                    Text("一键删除", fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = { deleteMode = !deleteMode },
                    enabled = items.isNotEmpty(),
                ) {
                    Text(if (deleteMode) "退出删除" else "删除收藏", fontSize = 13.sp)
                }
            }
        }
        if (deleteMode) {
            Text(
                text = if (historyMode) "按确定键删除当前选中记录" else "按确定键删除当前选中收藏",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        when {
            data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppLogoLoading(size = 84.dp)
                    Text("哎呀！正在加载中呢，你等等呀！", fontSize = 13.sp)
                }
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (historyMode) "暂无观看记录" else "暂无收藏内容",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 13.sp,
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.padding(top = 12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(items, key = { index, card -> "${card.key}:${card.id}:$index" }) { _, card ->
                    val cardKey = "${card.key}:${card.id}"
                    val requester = focusRequesters.getOrPut(cardKey) { FocusRequester() }
                    TvPosterCard(
                        title = card.title,
                        posterUrl = card.posterUrl,
                        subtitle = card.subtitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requester)
                            .onFocusChanged { if (it.isFocused) lastFocusedKey = cardKey },
                        onClick = {
                            if (deleteMode) pendingDelete = card else onCardClick(card)
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (historyMode) "删除观看记录" else "删除收藏") },
            text = { Text("确定删除《${card.title}》吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (historyMode) onDeleteHistory(card) else onDeleteFavorite(card)
                        pendingDelete = null
                        if (items.size <= 1) deleteMode = false
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text(if (historyMode) "一键删除观看记录" else "删除全部收藏") },
            text = {
                Text(
                    if (historyMode) "确定删除全部观看记录吗？删除后无法恢复。"
                    else "确定删除全部收藏吗？删除后无法恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (historyMode) onClearHistory() else onClearFavorites()
                        pendingClear = false
                        deleteMode = false
                    },
                ) { Text("全部删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) { Text("取消") }
            },
        )
    }
}
