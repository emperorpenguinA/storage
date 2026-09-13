package com.mementostorage.app.ui.components

import androidx.compose.ui.graphics.ImageBitmap

/** Decodes an encoded image (JPEG/PNG/etc.) into a bitmap Compose can draw, or null if it can't be decoded. */
expect fun decodeImageBitmapOrNull(bytes: ByteArray): ImageBitmap?
