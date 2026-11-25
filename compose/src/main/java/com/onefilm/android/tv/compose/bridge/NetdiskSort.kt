package com.onefilm.android.tv.compose.bridge

import java.util.Locale

/**
 * 网盘列表的排序与展示工具（TV / 手机共用，纯逻辑，不含 UI）。
 *
 * 排序字段：0=名称 1=修改时间 2=类型(扩展名) 3=大小。
 * 文件夹永远排在最前，与桌面资源管理器一致。
 */
object NetdiskSort {

    fun sorted(items: List<NetdiskEntry>, key: Int, ascending: Boolean): List<NetdiskEntry> {
        val byKey: Comparator<NetdiskEntry> = when (key) {
            1 -> compareBy<NetdiskEntry> { it.modified }.thenBy { it.name.lowercase() }
            2 -> compareBy<NetdiskEntry> { extensionOf(it.name) }.thenBy { it.name.lowercase() }
            3 -> compareBy<NetdiskEntry> { it.size }.thenBy { it.name.lowercase() }
            else -> compareBy { it.name.lowercase() }
        }
        val ordered = if (ascending) byKey else byKey.reversed()
        return items.sortedWith(
            compareBy<NetdiskEntry> { if (it.isDirectory) 0 else 1 }.then(ordered),
        )
    }

    /** 扩展名（小写，不含点）；无扩展名返回空串。 */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val kb = bytes / 1024.0
        return when {
            kb < 1024 -> String.format(Locale.US, "%.0f KB", kb)
            kb < 1024 * 1024 -> String.format(Locale.US, "%.1f MB", kb / 1024)
            else -> String.format(Locale.US, "%.2f GB", kb / 1024 / 1024)
        }
    }
}
