package com.mementostorage.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Opens the platform's photo/file picker; [onPicked] fires once with the chosen file's name,
 * MIME type and raw bytes. Returns a function to call (e.g. from a button's onClick) to
 * launch the picker.
 */
@Composable
expect fun rememberImagePicker(onPicked: (fileName: String, mimeType: String, bytes: ByteArray) -> Unit): () -> Unit
