package com.mementostorage.app.ui.components

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decoded thumbnails, keyed by "attachmentId:maxDimensionPx" so the same photo decoded at two
 * different sizes (a small list thumbnail vs. a larger field preview) doesn't collide. One
 * instance lives for the app's whole session (see [com.mementostorage.app.di.AppContainer]) so
 * scrolling a list back and forth doesn't repeat the decode work every time a row re-enters
 * the LazyColumn's composed window.
 */
class ImageBitmapCache {
    private val cache = mutableMapOf<String, ImageBitmap>()

    fun get(attachmentId: String, maxDimensionPx: Int): ImageBitmap? = cache[cacheKey(attachmentId, maxDimensionPx)]

    fun put(attachmentId: String, maxDimensionPx: Int, bitmap: ImageBitmap) {
        cache[cacheKey(attachmentId, maxDimensionPx)] = bitmap
    }

    private fun cacheKey(attachmentId: String, maxDimensionPx: Int) = "$attachmentId:$maxDimensionPx"
}
