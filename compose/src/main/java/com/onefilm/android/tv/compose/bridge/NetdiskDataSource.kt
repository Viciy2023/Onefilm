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

    suspend fun listEntries(drive: NetdiskDrive, path: String): List<NetdiskEntry>

    suspend fun resolve(drive: NetdiskDrive, entry: NetdiskEntry): NetdiskPlayback
}
