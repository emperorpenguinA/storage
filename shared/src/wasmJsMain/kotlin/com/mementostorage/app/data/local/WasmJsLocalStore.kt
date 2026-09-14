package com.mementostorage.app.data.local

import com.mementostorage.app.data.snapshot.AppDataSnapshot
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/**
 * Local storage for the web target: the whole dataset as one JSON document kept in the
 * browser's `localStorage`. This trades the relational querying SQLDelight gives Android for
 * a much smaller, dependable surface — appropriate for a personal database whose data is
 * realistically a few MB at most. See the note in shared/build.gradle.kts for why this
 * target does not use SQLDelight.
 *
 * All four wasmJs *Repository classes share one instance of this store (wired in
 * AppContainer.wasmJs.kt) so their in-memory view of the data stays consistent.
 */
private const val STORAGE_KEY = "memento_storage.snapshot.v1"

class WasmJsLocalStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<AppDataSnapshot> = _snapshot

    fun update(transform: (AppDataSnapshot) -> AppDataSnapshot) {
        _snapshot.update(transform)
        persist(_snapshot.value)
    }

    private fun load(): AppDataSnapshot {
        val raw = runCatching { localStorage.getItem(STORAGE_KEY) }.getOrNull() ?: return AppDataSnapshot.EMPTY
        return runCatching { json.decodeFromString(AppDataSnapshot.serializer(), raw) }.getOrDefault(AppDataSnapshot.EMPTY)
    }

    private fun persist(value: AppDataSnapshot) {
        runCatching { localStorage.setItem(STORAGE_KEY, json.encodeToString(AppDataSnapshot.serializer(), value)) }
    }
}
