package com.onefilm.android.tv.core.adblock

/**
 * 从 MPEG-TS 分片开头读取首个视频 PES 的 PTS（毫秒）。
 *
 * 只需读取约 2KB 即可命中首个 PES；解析失败返回 null，由调用方视为“无证据”。
 */
object TsPtsReader {

    fun readPtsMillis(bytes: ByteArray?): Long? {
        if (bytes == null || bytes.isEmpty()) return null
        val n = bytes.size
        var i = 0
        while (i <= n - 14) {
            if (bytes[i].toInt() == 0x00 &&
                bytes[i + 1].toInt() == 0x00 &&
                bytes[i + 2].toInt() == 0x01 &&
                (bytes[i + 3].toInt() and 0xFF) == 0xE0
            ) {
                val o = i + 9
                if (o + 4 >= n) return null
                val b0 = bytes[o].toInt() and 0xFF
                val b1 = bytes[o + 1].toInt() and 0xFF
                val b2 = bytes[o + 2].toInt() and 0xFF
                val b3 = bytes[o + 3].toInt() and 0xFF
                val b4 = bytes[o + 4].toInt() and 0xFF
                val pts = ((b0 and 0x0E).toLong() shl 29) or
                    (b1.toLong() shl 22) or
                    ((b2 and 0xFE).toLong() shl 14) or
                    (b3.toLong() shl 7) or
                    (b4 ushr 1).toLong()
                return pts * 1000L / 90000L
            }
            i++
        }
        return null
    }
}
