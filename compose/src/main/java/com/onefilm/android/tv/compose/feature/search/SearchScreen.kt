package com.onefilm.android.tv.compose.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val landing = state is SearchUiState.Idle

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
        if (landing) {
            SearchLanding(
                query = query,
                focusRequester = focusRequester,
                history = history,
                hot = hot,
                rankings = rankings,
                suggestions = suggestions,
                onQueryChange = {
                    query = it
                    onQueryChange(it)
                },
                onSearch = {
                    query = it
                    keyboard?.hide()
                    onSearch(it)
                },
                onClearHistory = onClearHistory,
            )
        } else {
            SearchResults(
                state = state,
                query = query,
                focusRequester = focusRequester,
                onQueryChange = {
                    query = it
                    onQueryChange(it)
                },
                onSearch = {
                    query = it
                    keyboard?.hide()
                    onSearch(it)
                },
                onRetry = onRetry,
                onCardClick = onCardClick,
            )
        }
    }
}

@Composable
private fun SearchLanding(
    query: String,
    focusRequester: FocusRequester,
    history: List<String>,
    hot: List<String>,
    rankings: List<SearchRank>,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        HistoryPanel(
            modifier = Modifier.weight(0.22f).fillMaxHeight(),
            history = history,
            onSearch = onSearch,
            onClearHistory = onClearHistory,
        )
        SearchCenterPanel(
            modifier = Modifier.weight(0.46f).fillMaxHeight(),
            query = query,
            focusRequester = focusRequester,
            suggestions = suggestions,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
        )
        RankingPanel(
            modifier = Modifier.weight(0.32f).fillMaxHeight(),
            hot = hot,
            rankings = rankings,
            onSearch = onSearch,
        )
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
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
            Text("搜索历史", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) {
                Text(
                    "清空",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(onClick = onClearHistory).padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (history.isEmpty()) {
            Text("暂无搜索历史", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                history.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { word ->
                            KeywordChip(
                                text = word,
                                modifier = Modifier.weight(1f),
                                onClick = { onSearch(word) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
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
    focusRequester: FocusRequester,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    Panel(modifier) {
        Text("搜索", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                placeholder = { Text("输入要搜索的影片、剧集、演员") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = { onSearch(query) }) { Text("搜索") }
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.take(6).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { word ->
                            KeywordChip(
                                text = word,
                                modifier = Modifier.weight(1f),
                                onClick = { onQueryChange(word); onSearch(word) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("字母索引", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        AlphabetGrid(onPick = { key -> onQueryChange(key); onSearch(key) })
        Spacer(Modifier.height(12.dp))
        Text(
            text = "点击字母或数字直接检索，也可以输入完整片名后点搜索。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun AlphabetGrid(onPick: (String) -> Unit) {
    val keys = ('A'..'Z').map { it.toString() } + (0..9).map { it.toString() }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        keys.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPick(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingPanel(
    modifier: Modifier,
    hot: List<String>,
    rankings: List<SearchRank>,
    onSearch: (String) -> Unit,
) {
    Panel(modifier) {
        Text("热门搜索", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (rankings.isEmpty()) {
                WordColumns(hot.take(18), onSearch)
            } else {
                rankings.forEach { rank ->
                    Text(
                        text = rank.title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    WordColumns(rank.words.take(18), onSearch)
                }
            }
        }
    }
}

@Composable
private fun WordColumns(words: List<String>, onSearch: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        words.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { word ->
                    KeywordChip(
                        text = word,
                        modifier = Modifier.weight(1f),
                        onClick = { onSearch(word) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KeywordChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            singleLine = true,
            placeholder = { Text("输入要搜索的影片、剧集、演员") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        )
        Spacer(Modifier.width(10.dp))
        Button(onClick = { onSearch(query) }) { Text("搜索") }
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = { onRetry() }) { Text("重试") }
    }
    Spacer(Modifier.height(12.dp))
    when (state) {
        SearchUiState.Idle, SearchUiState.Loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        is SearchUiState.Empty -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("没有找到“${state.keyword}”相关结果") }
        is SearchUiState.Error -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试") }
            }
        }
        is SearchUiState.Content -> LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "搜索(${state.completed}/${state.total})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            itemsIndexed(
                items = state.items,
                key = { index, card -> "${card.key}:${card.id}:$index" },
            ) { _, card ->
                TvPosterCard(
                    title = card.title,
                    posterUrl = card.posterUrl,
                    subtitle = card.subtitle,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCardClick(card) },
                )
            }
            if (!state.done) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}
