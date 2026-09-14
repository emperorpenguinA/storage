package com.mementostorage.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.browser.document
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.JsString

/** Opens a native `<input type="file">` picker. */
@Composable
actual fun rememberImagePicker(onPicked: (fileName: String, mimeType: String, bytes: ByteArray) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "image/*"
        input.onchange = { _: Event ->
            val file = input.files?.item(0)
            if (file != null) {
                scope.launch {
                    val bytes = readFileBytes(file)
                    onPicked(file.name, file.type.ifBlank { "application/octet-stream" }, bytes)
                }
            }
        }
        input.click()
    }
}

/**
 * Reads the file as a base64 data URL (`reader.readAsDataURL`) rather than an ArrayBuffer:
 * decoding a plain String with `kotlin.io.encoding.Base64` is stable, ordinary Kotlin, and
 * sidesteps `org.khronos.webgl` typed-array interop, whose exact operator surface for the
 * wasmJs target is easy to get subtly wrong.
 */
@OptIn(ExperimentalEncodingApi::class)
private suspend fun readFileBytes(file: File): ByteArray = suspendCancellableCoroutine { continuation ->
    val reader = FileReader()
    reader.onload = {
        val dataUrl = (reader.result as JsString).toString()
        val base64Payload = dataUrl.substringAfter(",", missingDelimiterValue = "")
        continuation.resume(Base64.decode(base64Payload))
    }
    reader.onerror = {
        continuation.resumeWithException(IllegalStateException("Failed to read the selected file"))
    }
    reader.readAsDataURL(file)
}
