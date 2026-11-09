package com.onefilm.android.tv.compose.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.SettingsSnapshot

/**
 * 弹幕设置面板的一项。
 * 普通项点击即在当前面板内切换/循环到下一个值，不再弹第二层选项面板；
 * 只有屏蔽词需要第二层文本输入。
 */
internal data class DanmakuItem(
    val label: String,
    val text: Boolean = false,
    val onClick: () -> Unit = {},
)

/**
 * 与设置页「弹幕」组一一对应的播放器面板数据。
 * 顺序、档位与设置页保持一致，改完立即写回 DanmakuSetting 并通知 DanmakuController 重新应用。
 */
@Composable
internal fun buildDanmakuItems(
    snapshot: SettingsSnapshot,
    onToggleShow: (Boolean) -> Unit,
    onChanged: () -> Unit,
): List<DanmakuItem> {
    val context = LocalContext.current
    fun s(id: Int, vararg args: Any): String = context.getString(id, *args)
    val ds = ComposeDependencies.settingsDataSource
    val on = s(R.string.common_on)
    val off = s(R.string.common_off)
    val rowsLabel = { n: Int -> if (n == 0) s(R.string.settings_danmaku_auto_rows) else s(R.string.settings_rows, n) }

    /** 点击后在当前面板内把值切到下一个（到末尾回到第一个），不打开二级面板。 */
    fun <T> cycle(values: List<T>, current: T, apply: (T) -> Unit): () -> Unit = {
        val index = values.indexOf(current)
        val next = if (index < 0) values.first() else values[(index + 1) % values.size]
        apply(next)
    }

    val styles = listOf(1 to R.string.settings_danmaku_style_stroke, 2 to R.string.settings_danmaku_style_shadow, 3 to R.string.settings_danmaku_style_projection, 0 to R.string.settings_danmaku_style_none)
    val colors = listOf(
        0 to R.string.settings_danmaku_color_default,
        1 to R.string.settings_danmaku_color_override,
        2 to R.string.settings_danmaku_color_random,
        3 to R.string.settings_danmaku_color_colorful,
        4 to R.string.settings_danmaku_color_gradient,
    )
    val scaleValues = listOf(0.8f, 1.0f, 1.2f, 1.5f)
    val opacityValues = listOf(0f, 0.25f, 0.5f, 0.7f)
    val speedValues = listOf(6000L, 8000L, 10000L)
    val lineValues = listOf(0, 4, 8, 12)
    val topValues = listOf(0, 1, 2, 3, 5)
    val offsetValues = listOf(-2000L, -1000L, 0L, 1000L, 2000L)
    val maxOnScreenValues = listOf(50, 100, 150, 200, 300)

    val styleLabel = s(styles.firstOrNull { it.first == snapshot.danmakuStyle }?.second ?: styles.first().second)
    val colorLabel = s(colors.firstOrNull { it.first == snapshot.danmakuColorMode }?.second ?: colors.first().second)

    return listOf(
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_show)} ${if (snapshot.danmakuShow) on else off}",
            onClick = { onToggleShow(!snapshot.danmakuShow) },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_load)} ${if (snapshot.danmakuLoad) on else off}",
            onClick = { ds.setDanmakuLoad(!snapshot.danmakuLoad); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_auto)} ${if (snapshot.danmakuAuto) on else off}",
            onClick = { ds.setDanmakuAuto(!snapshot.danmakuAuto); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_size)} ${"%.1fx".format(snapshot.danmakuScale)}",
            onClick = cycle(scaleValues, snapshot.danmakuScale) { ds.setDanmakuScale(it); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_opacity)} ${(snapshot.danmakuTransparency * 100).toInt()}%",
            onClick = cycle(opacityValues, snapshot.danmakuTransparency) { ds.setDanmakuTransparency(it); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_speed)} ${snapshot.danmakuDurationMs / 1000}s",
            onClick = cycle(speedValues, snapshot.danmakuDurationMs) { ds.setDanmakuDuration(it); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_scroll_lines)} ${rowsLabel(snapshot.danmakuLines)}",
            onClick = cycle(lineValues, snapshot.danmakuLines) { ds.setDanmakuLines(it); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_style)} $styleLabel",
            onClick = cycle(styles.map { it.first }, snapshot.danmakuStyle) { ds.setDanmakuStyle(it); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_color)} $colorLabel",
            onClick = cycle(colors.map { it.first }, snapshot.danmakuColorMode) { ds.setDanmakuColorMode(it); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_bold)} ${if (snapshot.danmakuBold) on else off}",
            onClick = { ds.setDanmakuBold(!snapshot.danmakuBold); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_top_lines)} ${rowsLabel(snapshot.danmakuTopLines)}",
            onClick = cycle(topValues, snapshot.danmakuTopLines) { ds.setDanmakuTopLines(it); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_bottom_lines)} ${rowsLabel(snapshot.danmakuBottomLines)}",
            onClick = cycle(topValues, snapshot.danmakuBottomLines) { ds.setDanmakuBottomLines(it); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_time_offset)} ${if (snapshot.danmakuTimeOffsetMs > 0) "+" else ""}${snapshot.danmakuTimeOffsetMs / 1000}s",
            onClick = cycle(offsetValues, snapshot.danmakuTimeOffsetMs) { ds.setDanmakuTimeOffsetMs(it); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_max_on_screen)} ${snapshot.danmakuMaxOnScreen}",
            onClick = cycle(maxOnScreenValues, snapshot.danmakuMaxOnScreen) { ds.setDanmakuMaxOnScreen(it); onChanged() },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_danmaku_block)} ${snapshot.danmakuBlockWords.ifBlank { s(R.string.settings_not_set) }}",
            text = true,
        ),
    )
}

/** 弹幕设置主面板：每一项点击即切换/循环，屏蔽词进入文本编辑页。 */
@Composable
internal fun DanmakuItemGrid(
    items: List<DanmakuItem>,
    firstFocus: FocusRequester,
    onActivate: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
    ) {
        itemsIndexed(items) { index, item ->
            FlatTile(
                text = item.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
            ) { onActivate(index) }
        }
    }
}

/** 屏蔽词编辑页：与设置页同一份存储，保存后立即生效。 */
@Composable
internal fun DanmakuTextPanel(
    initial: String,
    firstFocus: FocusRequester,
    onSave: (String) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.settings_danmaku_block),
            color = scheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = TextStyle(color = scheme.onSurface, fontSize = 13.sp),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp, max = 160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(8.dp)
                .focusRequester(firstFocus),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FlatTile(
                text = androidx.compose.ui.res.stringResource(R.string.settings_save),
                modifier = Modifier.heightIn(min = 22.dp),
            ) { onSave(draft.trim()) }
        }
    }
}
