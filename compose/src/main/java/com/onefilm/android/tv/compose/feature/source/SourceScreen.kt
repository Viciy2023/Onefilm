package com.onefilm.android.tv.compose.feature.source

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.bridge.SourceDataSource
import com.onefilm.android.tv.compose.bridge.SourceItem
import androidx.compose.ui.res.stringResource
import com.onefilm.android.tv.compose.R

@Composable
fun SourceRoute(
    title: String,
    dataSource: SourceDataSource,
    onBack: () -> Unit,
    onSelect: (SourceItem) -> Unit,
) {
    var items by remember(title) { mutableStateOf<List<SourceItem>?>(null) }
    var failed by remember(title) { mutableStateOf(false) }
    LaunchedEffect(title) {
        failed = false
        items = null
        runCatching { dataSource.load(title) }
            .onSuccess { items = it }
            .onFailure { failed = true }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.source_back, title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        when {
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.source_load_failed), style = MaterialTheme.typography.bodyLarge)
            }
            items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.onefilm.android.tv.compose.design.component.AppLoadingState()
            }
            items.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.source_no_other), style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items!!, key = { "${it.key}:${it.id}" }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onSelect(item) }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.siteName.ifBlank { stringResource(R.string.source_unknown_site) }, style = MaterialTheme.typography.titleMedium)
                            item.subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text("›", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        }
    }
}
