package com.onefilm.android.tv.core.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TsPtsReaderTest {

    private fun tsWithPts(pts: Long): ByteArray {
        val bytes = ByteArray(14)
        bytes[0] = 0x00
        bytes[1] = 0x00
        bytes[2] = 0x01
        bytes[3] = 0xE0.toByte()
        bytes[9] = (0x20 or ((pts ushr 29) and 0x0E).toInt() or 0x01).toByte()
        bytes[10] = ((pts ushr 22) and 0xFF).toByte()
        bytes[11] = (((pts ushr 14) and 0xFE) or 0x01).toByte()
        bytes[12] = ((pts ushr 7) and 0xFF).toByte()
        bytes[13] = (((pts shl 1) and 0xFE) or 0x01).toByte()
        return bytes
    }

    @Test
    fun `reads pts from first video pes`() {
        // 90000 ticks = 1s
        assertEquals(10_000L, TsPtsReader.readPtsMillis(tsWithPts(900_000)))
        assertEquals(1_480L, TsPtsReader.readPtsMillis(tsWithPts(133_200)))
    }

    @Test
    fun `skips leading garbage before pes`() {
        val prefix = ByteArray(200) { 0x47 }
        val bytes = prefix + tsWithPts(900_000)
        assertEquals(10_000L, TsPtsReader.readPtsMillis(bytes))
    }

    @Test
    fun `returns null for empty or non pes data`() {
        assertNull(TsPtsReader.readPtsMillis(null))
        assertNull(TsPtsReader.readPtsMillis(ByteArray(0)))
        assertNull(TsPtsReader.readPtsMillis(ByteArray(32) { 0x11 }))
    }
}
