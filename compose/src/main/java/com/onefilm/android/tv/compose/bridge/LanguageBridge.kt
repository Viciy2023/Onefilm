package com.onefilm.android.tv.compose.bridge

/**
 * 界面语言桥接。
 *
 * <p>compose 模块不依赖 catvod，语言读写由宿主（TV / 手机 Activity）注入。
 */
object LanguageBridge {

    /** 读取当前语言标签，空串表示跟随系统。 */
    var current: () -> String = { "" }

    /** 切换语言：宿主负责持久化并重建界面。 */
    var apply: (String) -> Unit = {}

    /** 语言标签对应的展示名。 */
    var labelOf: (String) -> String = { tag -> tag.ifBlank { "System" } }

    /** 可选语言（顺序即设置页展示顺序）。 */
    var tags: () -> List<String> = { listOf("", "zh-CN", "zh-TW", "en", "vi", "th") }
}
