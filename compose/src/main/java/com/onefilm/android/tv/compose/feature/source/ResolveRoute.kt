package com.onefilm.android.tv.compose.feature.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.bridge.SourceDataSource
import com.onefilm.android.tv.compose.bridge.SourceItem
import com.onefilm.android.tv.compose.design.component.FocusableSurface
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.res.stringResource
import com.onefilm.android.tv.compose.R

/**
 * 聚合入口：榜单/推荐卡片只有片名和海报时，
 * 按片名聚合搜索全部站点，拿到第一个可用的真实详情 ID 后直接进详情。
 */
@Composable
fun ResolveRoute(
    title: String,
    dataSource: SourceDataSource,
    onResolved: (SourceItem) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
) {
    var loading by remember(title) { mutableStateOf(true) }
    var failed by remember(title) { mutableStateOf(false) }
    var retryTick by remember(title) { mutableStateOf(0) }

    LaunchedEffect(title, retryTick) {
        loading = true
        failed = false
        val items = runCatching {
            withTimeoutOrNull(12_000) { dataSource.load(title) } ?: emptyList()
        }.getOrDefault(emptyList())
        val normalized = title.trim()
        val playable = items.filter { it.id.isNotBlank() && !it.id.startsWith("msearch:") }
        val best = playable.firstOrNull { it.title.trim() == normalized }
            ?: playable.firstOrNull { it.title.contains(normalized) || normalized.contains(it.title.trim()) }
            ?: playable.firstOrNull()
        if (best != null) {
            onResolved(best)
        } else {
            loading = false
            failed = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.source_back, title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    com.onefilm.android.tv.compose.design.component.AppLoadingState(
                        text = stringResource(R.string.source_aggregating, title),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.source_aggregate_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.source_none_playable), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.source_retry_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusableSurface(onClick = { retryTick++ }) {
                            Text(stringResource(R.string.detail_retry), modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                        }
                        FocusableSurface(onClick = onSearch) {
                            Text(stringResource(R.string.source_go_search), modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                        }
                        FocusableSurface(onClick = onBack) {
                            Text(stringResource(R.string.common_back), modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}
