package com.mementostorage.app.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

actual fun decodeImageBitmapOrNull(bytes: ByteArray, maxDimensionPx: Int): ImageBitmap? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    val longestEdge = maxOf(image.width, image.height)
    if (longestEdge <= maxDimensionPx) {
        return@runCatching image.toComposeImageBitmap()
    }
    // Camera photos routinely decode to multi-megapixel images; drawing the full-size decode
    // scaled down onto a small raster surface avoids holding that in memory just to shrink it
    // for a thumbnail, mirroring the Android side's BitmapFactory.Options.inSampleSize downscale.
    val scale = maxDimensionPx.toFloat() / longestEdge
    val targetWidth = (image.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (image.height * scale).toInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(targetWidth, targetHeight)
    surface.canvas.drawImageRect(
        image,
        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat()),
    )
    surface.makeImageSnapshot().toComposeImageBitmap()
}.getOrNull()
