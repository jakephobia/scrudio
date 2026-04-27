package com.scrudio.tv.ui.pair

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Tiny QR-code helper.
 *
 * Done in plain ZXing-core (no Android-specific deps) so the app stays
 * trim — we only need `encode()` → `BitMatrix` → manually drawn into a Bitmap.
 */
object QrCodeGenerator {

    /**
     * Renders [text] as a square QR Bitmap of [sizePx] × [sizePx] using
     * ARGB_8888 with a transparent quiet zone so the sage background bleeds
     * through naturally.
     */
    fun encode(text: String, sizePx: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val row = y * sizePx
            for (x in 0 until sizePx) {
                pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }
}
