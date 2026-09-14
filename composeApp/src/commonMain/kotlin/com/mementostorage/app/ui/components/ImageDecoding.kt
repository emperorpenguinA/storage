package com.mementostorage.app.ui.components

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes an encoded image (JPEG/PNG/etc.) into a bitmap Compose can draw, or null if it can't
 * be decoded. [maxDimensionPx] caps the decoded bitmap's longest edge — camera photos are
 * routinely 3000-5000px on a side, and decoding one at full resolution just to shrink it down
 * to a 48dp list thumbnail wastes both CPU and memory badly enough to visibly stall scrolling.
 * Pass a generous cap (or a very large one) when the caller actually needs detail, e.g. a
 * full-screen preview.
 */
expect fun decodeImageBitmapOrNull(bytes: ByteArray, maxDimensionPx: Int): ImageBitmap?
