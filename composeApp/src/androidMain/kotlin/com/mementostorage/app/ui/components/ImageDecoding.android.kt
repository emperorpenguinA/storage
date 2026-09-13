package com.mementostorage.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

actual fun decodeImageBitmapOrNull(bytes: ByteArray, maxDimensionPx: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching null
    bitmap.rotatedForExifOrientation(bytes).asImageBitmap()
}.getOrNull()

/**
 * Halves the decode resolution as many times as possible while staying at or above
 * [maxDimensionPx] on both edges — the standard `BitmapFactory.Options.inSampleSize` recipe
 * from Android's own "Loading Large Bitmaps Efficiently" guide. `inSampleSize` only accepts
 * powers of two, and the decoder rounds it to the nearest valid value anyway.
 */
private fun calculateInSampleSize(width: Int, height: Int, maxDimensionPx: Int): Int {
    var sampleSize = 1
    var sampledWidth = width
    var sampledHeight = height
    while (sampledWidth / 2 >= maxDimensionPx && sampledHeight / 2 >= maxDimensionPx) {
        sampledWidth /= 2
        sampledHeight /= 2
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * Camera/gallery photos are frequently stored with their raw pixels in landscape plus an EXIF
 * `Orientation` tag saying how a viewer should rotate/flip them — [BitmapFactory] decodes the
 * raw pixels only and ignores that tag, so without this the photo shows sideways or upside down.
 */
private fun Bitmap.rotatedForExifOrientation(originalBytes: ByteArray): Bitmap {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(originalBytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
