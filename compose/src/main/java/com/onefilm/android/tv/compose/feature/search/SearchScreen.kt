@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.onefilm.android.tv.compose.feature.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchRank
import com.onefilm.android.tv.compose.design.component.TvPosterCard

@Composable
fun SearchScreen(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    history: List<String> = emptyList(),
    hot: List<String> = emptyList(),
    rankings: List<SearchRank> = emptyList(),
    suggestions: List<String> = emptyList(),
    initialQuery: String? = null,
    onQueryChange: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery.orEmpty()) }
    val firstLetterFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val landing = state is SearchUiState.Idle

    Column(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 20.dp)) {
        if (landing) {
            SearchLanding(
                query = query,
                firstLetterFocus = firstLetterFocus,
                history = history,
                hot = hot,
                rankings = rankings,
                suggestions = suggestions,
                onQueryChange = {
                    query = it
                    onQueryChange(it)
                },
                onSearch = {
                    if (it.isNotBlank()) {
                        query = it
                        keyboard?.hide()
                        onSearch(it)
                    }
                },
                onClear = {
                    query = ""
                    onQueryChange("")
                },
                onDelete = {
                    if (query.isNotEmpty()) {
                        query = query.dropLast(1)
                        onQueryChange(query)
                    }
                },
                onClearHistory = onClearHistory,
            )
        } else {
            SearchResults(
                state = state,
                onCardClick = onCardClick,
            )
        }
    }
}

@Composable
private fun SearchLanding(
    query: String,
    firstLetterFocus: FocusRequester,
    history: List<String>,
    hot: List<String>,
    rankings: List<SearchRank>,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onClearHistory: () -> Unit,
) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HistoryPanel(
            modifier = Modifier.weight(0.20f).fillMaxHeight(),
            history = history,
            onSearch = onSearch,
            onClearHistory = onClearHistory,
        )
        SearchCenterPanel(
            modifier = Modifier.weight(0.36f).fillMaxHeight(),
            query = query,
            firstLetterFocus = firstLetterFocus,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            onDelete = onDelete,
        )
        DynamicRightPanel(
            modifier = Modifier.weight(0.44f).fillMaxHeight(),
            query = query,
            hot = hot,
            rankings = rankings,
            suggestions = suggestions,
            onSearch = onSearch,
        )
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) { content() }
}

@Composable
private fun HistoryPanel(
    modifier: Modifier,
    history: List<String>,
    onSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Panel(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("搜索历史", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) {
                Text(
                    "清空",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable(onClick = onClearHistory).padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        if (history.isEmpty()) {
            Text("暂无搜索历史", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    history.forEach { word ->
                        KeywordChip(text = word, onClick = { onSearch(word) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCenterPanel(
    modifier: Modifier,
    query: String,
    firstLetterFocus: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Panel(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            placeholder = { Text("输入要搜索的影片、剧集、演员", fontSize = 18.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSearch(query) }, modifier = Modifier.weight(1f)) {
                Text("搜索", fontSize = 17.sp)
            }
            OutlinedButton(onClick = { onSearch(query) }, modifier = Modifier.weight(1f)) {
                Text("远程搜索", fontSize = 17.sp)
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text("清空", fontSize = 17.sp)
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text("删除", fontSize = 17.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("字母索引", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        AlphabetGrid(
            modifier = Modifier.weight(1f),
            firstLetterFocus = firstLetterFocus,
            onPick = { key -> onQueryChange(query + key) },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "字母只填入搜索框；输入完整关键字后点「搜索」。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun AlphabetGrid(
    modifier: Modifier,
    firstLetterFocus: FocusRequester,
    onPick: (String) -> Unit,
) {
    val keys = ('A'..'Z').map { it.toString() } + (0..9).map { it.toString() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1200)
        firstLetterFocus.requestFocus()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(6).forEachIndexed { rowIndex, row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIndex, key ->
                    AlphabetKey(
                        key = key,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        focusRequester = if (rowIndex == 0 && colIndex == 0) firstLetterFocus else null,
                        onClick = { onPick(key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlphabetKey(
    key: String,
    modifier: Modifier,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(110), label = "letter")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            color = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DynamicRightPanel(
    modifier: Modifier,
    query: String,
    hot: List<String>,
    rankings: List<SearchRank>,
    suggestions: List<String>,
    onSearch: (String) -> Unit,
) {
    Panel(modifier) {
        Text(
            text = if (query.isBlank()) "热门搜索" else "匹配结果",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (query.isNotBlank()) {
                if (suggestions.isEmpty()) {
                    Text(
                        "正在匹配，输入完整片名后可点「搜索」。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        suggestions.take(40).forEach { word ->
                            KeywordChip(text = word, onClick = { onSearch(word) })
                        }
                    }
                }
            } else if (rankings.isEmpty()) {
                WordColumns(hot.take(24), onSearch)
            } else {
                rankings.forEach { rank ->
                    Text(
                        text = rank.title,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    WordColumns(rank.words.take(24), onSearch)
                }
            }
        }
    }
}

@Composable
private fun WordColumns(words: List<String>, onSearch: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        words.forEach { word ->
            KeywordChip(text = word, onClick = { onSearch(word) })
        }
    }
}

@Composable
private fun KeywordChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 21.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    onCardClick: (MediaCard) -> Unit,
) {
    val content = state as? SearchUiState.Content
    val items = content?.items.orEmpty()
    val sites = remember(content?.keyword, items.size) {
        items.map { it.siteName?.takeIf { name -> name.isNotBlank() } ?: it.key }
            .filter { it.isNotBlank() }
            .distinct()
    }
    var selectedSite by remember(content?.keyword) { mutableStateOf<String?>(null) }
    val visibleItems = if (selectedSite == null) items else items.filter {
        (it.siteName?.takeIf { name -> name.isNotBlank() } ?: it.key) == selectedSite
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
            Modifier
                .width(340.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "搜索",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = content?.let { "(${it.completed}/${it.total})" } ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SiteButton(
                    text = "全部显示",
                    selected = selectedSite == null,
                    onClick = { selectedSite = null },
                )
                sites.forEach { site ->
                    SiteButton(
                        text = site,
                        selected = selectedSite == site,
                        onClick = { selectedSite = site },
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (state) {
                SearchUiState.Idle, SearchUiState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                is SearchUiState.Empty -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("没有找到“${state.keyword}”相关结果", fontSize = 18.sp) }
                is SearchUiState.Error -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text(state.message, fontSize = 18.sp, color = MaterialTheme.colorScheme.error) }
                is SearchUiState.Content -> LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    itemsIndexed(
                        items = visibleItems,
                        key = { index, card -> "${card.key}:${card.id}:$index" },
                    ) { _, card ->
                        TvPosterCard(
                            title = card.title,
                            posterUrl = card.posterUrl,
                            subtitle = card.subtitle,
                            modifier = Modifier.fillMaxWidth(),
                            sourceLabel = card.siteName?.takeIf { it.isNotBlank() } ?: card.key,
                            onClick = { onCardClick(card) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        fontSize = 23.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}