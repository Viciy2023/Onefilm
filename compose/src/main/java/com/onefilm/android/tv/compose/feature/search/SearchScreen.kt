package com.onefilm.android.tv.compose.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.component.PosterCard

@Composable
fun SearchScreen(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    history: List<String> = emptyList(),
    hot: List<String> = emptyList(),
    suggestions: List<String> = emptyList(),
    onQueryChange: (String) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("搜索", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onQueryChange(it) },
                modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { if (it.isFocused) keyboard?.show() },
                singleLine = true,
                placeholder = { Text("搜索影片、剧集、演员") },
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Button(onClick = { onSearch(query) }) { Text("搜索") }
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(6).forEach { word ->
                    Text(
                        text = word,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable { onSearch(word) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        when (state) {
            SearchUiState.Idle -> IdleContent(history, hot) { word -> query = word; onSearch(word) }
            SearchUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is SearchUiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有找到“${state.keyword}”相关结果", style = MaterialTheme.typography.bodyLarge)
            }
            is SearchUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text("重试") }
                }
            }
            is SearchUiState.Content -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("搜索(${state.completed}/${state.total})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                }
                itemsIndexed(
                    items = state.items,
                    key = { index, card -> "${card.key}:${card.id}:$index" },
                ) { _, card ->
                    PosterCard(
                        title = card.title,
                        posterUrl = card.posterUrl,
                        subtitle = card.subtitle,
                        onClick = { onCardClick(card) },
                    )
                }
                if (!state.done) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleContent(history: List<String>, hot: List<String>, onPick: (String) -> Unit) {
    Column {
        if (history.isNotEmpty()) {
            Text("搜索历史", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { history.take(8).forEach { w -> Text(w, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).clickable { onPick(w) }.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium) } }
            Spacer(Modifier.height(20.dp))
        }
        Text(
            text = "热门搜索",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            hot.take(12).forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onPick(label) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
