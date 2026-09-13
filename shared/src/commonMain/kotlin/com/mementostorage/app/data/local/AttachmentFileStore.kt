package com.mementostorage.app.data.local

/**
 * Reads/writes the binary content a PHOTO field points at. [EntryAttachment.localPath] is an
 * opaque key into this store — a filesystem path on Android, an IndexedDB key on wasmJs.
 */
interface AttachmentFileStore {
    suspend fun readBytes(localPath: String): ByteArray?
    suspend fun writeBytes(fileName: String, bytes: ByteArray): String
}
