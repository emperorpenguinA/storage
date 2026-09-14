@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.mementostorage.app.data.local

import com.mementostorage.app.util.IdGenerator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val DB_NAME = "memento_storage_attachments"
private const val STORE_NAME = "files"
private const val DB_VERSION = 1

private external interface IdbRequest : JsAny {
    var onsuccess: (() -> Unit)?
    var onerror: (() -> Unit)?
}

private external interface IdbOpenRequest : IdbRequest {
    var onupgradeneeded: (() -> Unit)?
}

private external interface IdbDatabase : JsAny
private external interface IdbTransaction : JsAny
private external interface IdbObjectStore : JsAny

private fun jsOpenDb(name: String, version: Int): IdbOpenRequest = js("indexedDB.open(name, version)")
private fun jsOpenRequestDb(request: IdbOpenRequest): IdbDatabase = js("request.result")
private fun jsHasObjectStore(db: IdbDatabase, name: String): Boolean = js("db.objectStoreNames.contains(name)")
private fun jsCreateObjectStore(db: IdbDatabase, name: String): JsAny = js("db.createObjectStore(name)")
private fun jsTransaction(db: IdbDatabase, storeName: String, mode: String): IdbTransaction = js("db.transaction(storeName, mode)")
private fun jsObjectStore(tx: IdbTransaction, name: String): IdbObjectStore = js("tx.objectStore(name)")
private fun jsPut(store: IdbObjectStore, value: String, key: String): IdbRequest = js("store.put(value, key)")
private fun jsGet(store: IdbObjectStore, key: String): IdbRequest = js("store.get(key)")
private fun jsGetResult(request: IdbRequest): String? = js("request.result")

/**
 * Persistent byte storage for the web target, backed by IndexedDB so downloaded/picked photo
 * bytes survive page reloads -- unlike a plain in-memory map, which forced every attachment
 * restored from a Drive backup to be re-downloaded from Drive on every fresh page load. Bytes
 * are kept base64-encoded (like WasmJsLocalStore's JSON snapshot) rather than as raw typed
 * arrays, trading a bit of storage overhead for staying on the same simple, dependable
 * String-based interop this web target already relies on elsewhere.
 */
class WasmJsAttachmentFileStore : AttachmentFileStore {
    private val dbDeferred = CompletableDeferred<IdbDatabase>()

    init {
        val request = jsOpenDb(DB_NAME, DB_VERSION)
        request.onupgradeneeded = {
            val db = jsOpenRequestDb(request)
            if (!jsHasObjectStore(db, STORE_NAME)) {
                jsCreateObjectStore(db, STORE_NAME)
            }
        }
        request.onsuccess = { dbDeferred.complete(jsOpenRequestDb(request)) }
        request.onerror = { dbDeferred.completeExceptionally(IllegalStateException("Failed to open IndexedDB")) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun readBytes(localPath: String): ByteArray? {
        val db = dbDeferred.await()
        val base64 = suspendCancellableCoroutine { continuation ->
            val store = jsObjectStore(jsTransaction(db, STORE_NAME, "readonly"), STORE_NAME)
            val request = jsGet(store, localPath)
            request.onsuccess = { continuation.resume(jsGetResult(request)) }
            request.onerror = { continuation.resume(null) }
        }
        return base64?.let { runCatching { Base64.decode(it) }.getOrNull() }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun writeBytes(fileName: String, bytes: ByteArray): String {
        val key = "${IdGenerator.newId()}_$fileName"
        val db = dbDeferred.await()
        val base64 = Base64.encode(bytes)
        suspendCancellableCoroutine<Unit> { continuation ->
            val store = jsObjectStore(jsTransaction(db, STORE_NAME, "readwrite"), STORE_NAME)
            val request = jsPut(store, base64, key)
            request.onsuccess = { continuation.resume(Unit) }
            request.onerror = { continuation.resumeWithException(IllegalStateException("Failed to write attachment bytes")) }
        }
        return key
    }
}
