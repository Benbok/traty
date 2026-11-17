package ru.my.traty.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.my.traty.ai.receipt.ReceiptException
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Фото чека для Vision OCR: не больше [MAX_SIDE] точек по длинной стороне (OCR принимает до 20 Мп и 10 МБ,
 * а камера снимает по 50 Мп), повёрнуто по EXIF, в JPEG. Мелкий шрифт кассовой ленты на 2560 точках ещё читается.
 */
object ReceiptImage {
    const val MAX_SIDE = 2560
    private const val QUALITY = 88
    private const val MAX_BYTES = 9 * 1024 * 1024

    suspend fun prepare(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // С inJustDecodeBounds decodeStream всегда возвращает null: размеры приходят в bounds
        val input = resolver.openInputStream(uri) ?: throw ReceiptException("Фото не открылось, попробуйте ещё раз")
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ReceiptException("Это не похоже на фото")
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw ReceiptException("Фото не открылось, попробуйте ещё раз")
        val rotation = resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
        val scaled = scaleAndRotate(decoded, rotation)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        out.toByteArray().also { if (it.size > MAX_BYTES) throw ReceiptException("Фото слишком большое") }
    }

    /** Степень двойки, чтобы декодировать не крупнее, чем нужно (экономит память). */
    internal fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= MAX_SIDE) sample *= 2
        return sample
    }

    private fun exifRotation(exif: ExifInterface): Int = when (
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun scaleAndRotate(src: Bitmap, rotation: Int): Bitmap {
        val longSide = max(src.width, src.height)
        val scale = if (longSide > MAX_SIDE) MAX_SIDE.toFloat() / longSide else 1f
        if (scale == 1f && rotation == 0) return src
        val m = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
