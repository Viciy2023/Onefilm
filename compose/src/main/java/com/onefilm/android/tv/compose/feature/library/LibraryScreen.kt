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
import com.onefilm.android.tv.compose.design.PosterStyleController
import com.onefilm.android.tv.compose.design.component.PosterCard
import com.onefilm.android.tv.compose.design.component.TvTagButton
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.onefilm.android.tv.compose.R

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

    // 从详情返回时恢复上次的卡片焦点；首次从左侧导航进入时不要抢焦点，
    // 否则用户在导航上按向下会被内容区的几何焦点搜索带到线路/网盘。
    LaunchedEffect(items.size) {
        val key = lastFocusedKey ?: return@LaunchedEffect
        if (items.isEmpty()) return@LaunchedEffect
        // 等 LazyVerticalGrid 完成布局、卡片 FocusRequester 注册后再恢复焦点。
        delay(120)
        runCatching { focusRequesters[key]?.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().padding(start = 12.dp, top = 2.dp, end = 12.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (historyMode) {
                TvTagButton(
                    text = if (deleteMode) stringResource(R.string.library_exit_delete) else stringResource(R.string.library_delete_records),
                    enabled = items.isNotEmpty(),
                ) { deleteMode = !deleteMode }
                Spacer(Modifier.width(8.dp))
                TvTagButton(
                    text = stringResource(R.string.library_delete_all),
                    enabled = items.isNotEmpty(),
                ) { pendingClear = true }
            } else {
                TvTagButton(
                    text = if (deleteMode) stringResource(R.string.library_exit_delete) else stringResource(R.string.library_delete_favorites),
                    enabled = items.isNotEmpty(),
                ) { deleteMode = !deleteMode }
            }
        }
        if (deleteMode) {
            Text(
                text = if (historyMode) stringResource(R.string.library_hint_delete_record) else stringResource(R.string.library_hint_delete_favorite),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        when {
            data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppLogoLoading(size = 84.dp)
                    Text(stringResource(R.string.player_loading_hint), fontSize = 13.sp)
                }
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (historyMode) stringResource(R.string.library_empty_history) else stringResource(R.string.library_empty_favorites),
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 13.sp,
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(if (PosterStyleController.horizontal) 4 else 6),
                modifier = Modifier.padding(top = 12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(items, key = { index, card -> "${card.key}:${card.id}:$index" }) { _, card ->
                    val cardKey = "${card.key}:${card.id}"
                    val requester = focusRequesters.getOrPut(cardKey) { FocusRequester() }
                    PosterCard(
                        title = card.title,
                        posterUrl = card.posterUrl,
                        landscapeUrl = card.landscapeUrl,
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
            title = { Text(if (historyMode) stringResource(R.string.library_delete_history_title) else stringResource(R.string.library_delete_favorites)) },
            text = { Text(stringResource(R.string.library_confirm_delete, card.title)) },
            confirmButton = {
                TvTagButton(
                    text = stringResource(R.string.common_delete),
                    onClick = {
                        if (historyMode) onDeleteHistory(card) else onDeleteFavorite(card)
                        pendingDelete = null
                        if (items.size <= 1) deleteMode = false
                    },
                )
            },
            dismissButton = {
                TvTagButton(text = stringResource(R.string.settings_cancel), onClick = { pendingDelete = null })
            },
        )
    }

    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text(if (historyMode) stringResource(R.string.library_delete_all_history_title) else stringResource(R.string.library_delete_all_favorites_title)) },
            text = {
                Text(
                    if (historyMode) stringResource(R.string.library_confirm_delete_all_history)
                    else stringResource(R.string.library_confirm_delete_all_favorites),
                )
            },
            confirmButton = {
                TvTagButton(
                    text = stringResource(R.string.library_delete_all_confirm),
                    onClick = {
                        if (historyMode) onClearHistory() else onClearFavorites()
                        pendingClear = false
                        deleteMode = false
                    },
                )
            },
            dismissButton = {
                TvTagButton(text = stringResource(R.string.settings_cancel), onClick = { pendingClear = false })
            },
        )
    }
}
