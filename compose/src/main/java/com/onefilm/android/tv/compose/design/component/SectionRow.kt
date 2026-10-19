package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PosterItem(
    val key: String,
    val id: String,
    val title: String,
    val posterUrl: String?,
    val subtitle: String?,
)

@Composable
fun SectionRow(
    title: String,
    items: List<PosterItem>,
    onItemClick: (PosterItem) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { index, item -> "${item.key}:${item.id}:$index" },
            ) { _, item ->
                PosterCard(
                    title = item.title,
                    posterUrl = item.posterUrl,
                    subtitle = item.subtitle,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}
