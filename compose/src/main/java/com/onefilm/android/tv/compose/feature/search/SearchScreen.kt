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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchRank
import com.onefilm.android.tv.compose.design.component.TvPosterCard
import com.onefilm.android.tv.compose.design.component.TvTagButton
import com.onefilm.android.tv.compose.navigation.TvBrandRow
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource

private object SearchFocusState {
    var query: String? = null
    var cardKey: String? = null
}

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
    focusToken: Int = 0,
    onQueryChange: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery.orEmpty()) }
    val firstLetterFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hasInitialQuery = !initialQuery.isNullOrBlank()
    // A card/category click passes an initial query: show the result view
    // (loading or content) immediately, never the search landing page.
    val landing = state is SearchUiState.Idle && !hasInitialQuery

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    start = 12.dp,
                    top = 2.dp,
                    end = 12.dp,
                    bottom = 10.dp,
                ),
        ) {
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
                focusToken = focusToken,
                onCardClick = onCardClick,
            )
        }
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HistoryPanel(
            modifier = Modifier.weight(0.22f).fillMaxHeight(),
            history = history,
            onSearch = onSearch,
            onClearHistory = onClearHistory,
        )
        SearchCenterPanel(
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
            query = query,
            firstLetterFocus = firstLetterFocus,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            onDelete = onDelete,
        )
        DynamicRightPanel(
            modifier = Modifier.weight(0.36f).fillMaxHeight(),
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
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(6.dp),
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
            Text(stringResource(R.string.search_history), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) {
                Text(
                    stringResource(R.string.search_clear),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onClearHistory).padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (history.isEmpty()) {
            Text(stringResource(R.string.search_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TvTagButton(text = stringResource(R.string.search_action), modifier = Modifier.weight(1f)) { onSearch(query) }
            TvTagButton(text = stringResource(R.string.search_remote), modifier = Modifier.weight(1f)) { onSearch(query) }
            TvTagButton(text = stringResource(R.string.search_clear), modifier = Modifier.weight(1f)) { onClear() }
            TvTagButton(text = stringResource(R.string.search_delete), modifier = Modifier.weight(1f)) { onDelete() }
        }
        Spacer(Modifier.height(6.dp))
        AlphabetGrid(
            modifier = Modifier.weight(1f),
            firstLetterFocus = firstLetterFocus,
            onPick = { key -> onQueryChange(query + key) },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.search_keyboard_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
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
        // Focus the alphabet immediately. The old 1200ms delay let the
        // history "清空" button take initial focus before jumping here.
        kotlinx.coroutines.delay(40)
        runCatching { firstLetterFocus.requestFocus() }
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
            fontSize = 13.sp,
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
            text = if (query.isBlank()) stringResource(R.string.search_hot) else stringResource(R.string.search_match_result),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (query.isNotBlank()) {
                if (suggestions.isEmpty()) {
                    Text(
                        stringResource(R.string.search_matching),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    WordColumns(rank.words.take(24), onSearch)
                }
            }
        }
    }
}

@Composable
private fun WordColumns(words: List<String>, onSearch: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    focusToken: Int = 0,
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
    val cardFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var restoreComplete by remember(focusToken) { mutableStateOf(false) }
    var autoFocusedKeyword by remember { mutableStateOf<String?>(null) }
    val restoringCard = !restoreComplete &&
        SearchFocusState.cardKey != null &&
        SearchFocusState.query == content?.keyword
    LaunchedEffect(focusToken, content?.keyword, visibleItems.size, selectedSite) {
        if (SearchFocusState.query != content?.keyword) {
            SearchFocusState.cardKey = null
        }
        val target = SearchFocusState.cardKey
        if (target != null && SearchFocusState.query == content?.keyword) {
            // Compose navigation restores focus late on some TV devices.
            // Retry briefly so returning from detail lands on the card again.
            repeat(8) {
                runCatching { cardFocusRequesters[target]?.requestFocus() }
                delay(60)
            }
            restoreComplete = true
        } else {
            restoreComplete = true
            // 新关键词的首批结果到达后，把初始焦点从左侧站点栏移到第一张卡片。
            // 只在关键词变化时执行一次，切换站点筛选不会反复抢焦点。
            val keyword = content?.keyword
            if (keyword != null && visibleItems.isNotEmpty() && autoFocusedKeyword != keyword) {
                autoFocusedKeyword = keyword
                delay(120)
                val firstKey = "${visibleItems.first().key}:${visibleItems.first().id}"
                runCatching { cardFocusRequesters[firstKey]?.requestFocus() }
            }
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier
                .width(160.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = content?.let { "(${it.completed}/${it.total})" } ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SiteButton(
                    text = stringResource(R.string.search_show_all),
                    selected = selectedSite == null,
                    focusEnabled = !restoringCard,
                    onClick = { selectedSite = null },
                )
                sites.forEach { site ->
                    SiteButton(
                        text = site,
                        selected = selectedSite == site,
                        focusEnabled = !restoringCard,
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
                ) { com.onefilm.android.tv.compose.design.component.AppLoadingState() }
                is SearchUiState.Empty -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.search_no_result, state.keyword), fontSize = 13.sp) }
                is SearchUiState.Error -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text(state.message.ifBlank { stringResource(R.string.search_load_failed) }, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
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
                        val cardKey = "${card.key}:${card.id}"
                        val requester = cardFocusRequesters.getOrPut(cardKey) { FocusRequester() }
                        TvPosterCard(
                            title = card.title,
                            posterUrl = card.posterUrl,
                            subtitle = card.subtitle,
                            modifier = Modifier.fillMaxWidth().focusRequester(requester),
                            sourceLabel = card.siteName?.takeIf { it.isNotBlank() } ?: card.key,
                            onClick = {
                                SearchFocusState.query = content?.keyword
                                SearchFocusState.cardKey = cardKey
                                onCardClick(card)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteButton(
    text: String,
    selected: Boolean,
    focusEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
            .focusProperties { canFocus = focusEnabled }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    )
}
