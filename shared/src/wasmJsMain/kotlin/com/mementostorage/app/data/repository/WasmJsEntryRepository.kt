package com.mementostorage.app.data.repository

import com.mementostorage.app.data.local.WasmJsLocalStore
import com.mementostorage.app.data.snapshot.EntryDto
import com.mementostorage.app.data.snapshot.toDomain
import com.mementostorage.app.data.snapshot.toDto
import com.mementostorage.app.domain.model.Entry
import com.mementostorage.app.domain.repository.EntryRepository
import com.mementostorage.app.util.IdGenerator
import com.mementostorage.app.util.nowEpochMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WasmJsEntryRepository(private val store: WasmJsLocalStore) : EntryRepository {

    override fun observeEntries(libraryId: String): Flow<List<Entry>> =
        store.snapshot.map { snapshot ->
            val attachmentsByEntry = snapshot.attachments.groupBy { it.entryId }
            snapshot.entries
                .filter { it.libraryId == libraryId }
                .sortedByDescending { it.updatedAt }
                .map { entry -> entry.toDomain(attachmentsByEntry[entry.id].orEmpty().map { it.toDomain() }) }
        }

    override suspend fun getEntry(id: String): Entry? {
        val snapshot = store.snapshot.value
        val row = snapshot.entries.firstOrNull { it.id == id } ?: return null
        val attachments = snapshot.attachments.filter { it.entryId == id }
        return row.toDomain(attachments.map { it.toDomain() })
    }

    override suspend fun createEntry(libraryId: String, values: Map<String, String>): Entry {
        val id = IdGenerator.newId()
        val now = nowEpochMillis()
        store.update { snapshot ->
            snapshot.copy(entries = snapshot.entries + EntryDto(id, libraryId, values, now, now))
        }
        return getEntry(id)!!
    }

    override suspend fun updateEntry(id: String, values: Map<String, String>) {
        store.update { snapshot ->
            snapshot.copy(
                entries = snapshot.entries.map {
                    if (it.id == id) it.copy(values = values, updatedAt = nowEpochMillis()) else it
                },
            )
        }
    }

    override suspend fun deleteEntry(id: String) {
        store.update { snapshot ->
            snapshot.copy(
                entries = snapshot.entries.filterNot { it.id == id },
                attachments = snapshot.attachments.filterNot { it.entryId == id },
            )
        }
    }

    override suspend fun getAllEntries(libraryIds: List<String>): List<Entry> {
        val snapshot = store.snapshot.value
        val idSet = libraryIds.toSet()
        val attachmentsByEntry = snapshot.attachments.groupBy { it.entryId }
        return snapshot.entries
            .filter { it.libraryId in idSet }
            .map { entry -> entry.toDomain(attachmentsByEntry[entry.id].orEmpty().map { it.toDomain() }) }
    }

    override suspend fun upsertEntry(entry: Entry) {
        val dto = entry.toDto()
        store.update { snapshot ->
            snapshot.copy(entries = snapshot.entries.filterNot { it.id == entry.id } + dto)
        }
    }
}
