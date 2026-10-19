package com.onefilm.android.tv.compose.feature.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.bridge.CategoryPage
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.component.PosterCard

@Composable
fun CategoryScreen(
    title: String,
    state: CategoryUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
) {
    when (state) {
        CategoryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is CategoryUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试") }
            }
        }
        is CategoryUiState.Content -> CategoryContent(title, state.page, onBack, onCardClick)
    }
}

@Composable
private fun CategoryContent(
    fallbackTitle: String,
    page: CategoryPage,
    onBack: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            text = "‹ ${page.title.ifBlank { fallbackTitle }}",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(16.dp)
                .clickable(onClick = onBack),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(
                items = page.items,
                key = { index, card -> "${card.key}:${card.id}:$index" },
            ) { _, card ->
                PosterCard(
                    title = card.title,
                    posterUrl = card.posterUrl,
                    subtitle = card.subtitle,
                    onClick = { onCardClick(card) },
                )
            }
        }
    }
}
