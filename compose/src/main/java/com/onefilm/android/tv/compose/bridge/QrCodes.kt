package com.onefilm.android.tv.compose.bridge

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** 把文本内容渲染成二维码位图；两端各自用 Image 显示，不共用页面布局。 */
object QrCodes {

    fun bitmap(content: String, size: Int = 480): Bitmap? = runCatching {
        if (content.isBlank() || size <= 0) return null
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    /**
     * 接口有时直接返回 base64 的二维码图片（例如 UC TV Token 的
     * `data:image/png;base64,...`），这种就不要再编码一次，直接解码显示。
     */
    fun bitmapFromDataUri(uri: String): Bitmap? = runCatching {
        val comma = uri.indexOf(',')
        if (comma <= 0) return null
        val bytes = android.util.Base64.decode(uri.substring(comma + 1), android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    /** 内容是不是接口给的图片而不是二维码文本。 */
    fun isImage(uri: String): Boolean =
        uri.startsWith("data:image", ignoreCase = true)
}
