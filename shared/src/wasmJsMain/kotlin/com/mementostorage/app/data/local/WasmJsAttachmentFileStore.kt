package com.mementostorage.app.data.local

import com.mementostorage.app.util.IdGenerator

/**
 * Holds picked-but-not-yet-synced photo bytes in memory only. Browser storage large enough
 * for photos (IndexedDB) needs its own async plumbing that this scaffold intentionally
 * leaves out to keep the web target on simple, dependable APIs (see WasmJsLocalStore); the
 * practical effect is that an attachment must finish uploading to Drive before a page reload,
 * or its bytes are lost (the metadata row survives, but syncState stays PENDING with nothing
 * left to upload). Wiring an IndexedDB-backed implementation of this interface is a natural
 * follow-up once the rest of the app is working.
 */
class WasmJsAttachmentFileStore : AttachmentFileStore {
    private val pending = mutableMapOf<String, ByteArray>()

    override suspend fun readBytes(localPath: String): ByteArray? = pending[localPath]

    override suspend fun writeBytes(fileName: String, bytes: ByteArray): String {
        val key = "${IdGenerator.newId()}_$fileName"
        pending[key] = bytes
        return key
    }
}
