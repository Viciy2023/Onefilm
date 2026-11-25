package com.onefilm.android.tv.compose.bridge

data class NetdiskDrive(
    val id: Int,
    val name: String,
    val type: Int,
    val configJson: String,
)

data class NetdiskEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val fileUrl: String? = null,
    /** 文件大小（字节），目录为 0。 */
    val size: Long = 0L,
    /** 最后修改时间（毫秒），取不到为 0。 */
    val modified: Long = 0L,
)

data class NetdiskPlayback(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/** WebDAV / Alist 网盘数据源。 */
interface NetdiskDataSource {
    fun listDrives(): List<NetdiskDrive>

    fun saveDrive(drive: NetdiskDrive): NetdiskDrive

    fun deleteDrive(id: Int)

    suspend fun listEntries(drive: NetdiskDrive, path: String, page: Int = 1): List<NetdiskEntry>

    suspend fun resolve(drive: NetdiskDrive, entry: NetdiskEntry): NetdiskPlayback

    /**
     * 排序方式：0=名称 1=修改时间 2=类型(扩展名) 3=大小。
     * 实际排序在 UI 层做，这里只负责持久化用户选择。
     */
    fun sortKey(): Int = 0

    fun setSortKey(key: Int) {}

    /** 升序/降序（文件夹始终排在前面）。 */
    fun sortAscending(): Boolean = true

    fun setSortAscending(ascending: Boolean) {}
}
