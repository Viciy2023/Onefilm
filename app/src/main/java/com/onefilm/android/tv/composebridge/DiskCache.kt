package com.onefilm.android.tv.composebridge

import com.github.catvod.utils.Path
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * OneFilm: small persistent cache for bridge models. Files live under
 * Path.cache, so the existing "清除缓存" action removes them together with
 * Coil/HTTP/player caches.
 */
object DiskCache {

    private fun file(name: String): File = Path.cache("onefilm_$name.ser")

    fun write(name: String, value: Any) {
        runCatching {
            val target = file(name)
            FileOutputStream(target).use { output ->
                ObjectOutputStream(output).use { it.writeObject(value) }
            }
        }
    }

    fun <T> read(name: String, maxAgeMs: Long = 10 * 60_000L): T? = runCatching {
        val source = file(name)
        if (!source.exists()) return null
        if (maxAgeMs > 0 && System.currentTimeMillis() - source.lastModified() > maxAgeMs) return null
        FileInputStream(source).use { input ->
            ObjectInputStream(input).use { it.readObject() as T }
        }
    }.getOrNull()
}
