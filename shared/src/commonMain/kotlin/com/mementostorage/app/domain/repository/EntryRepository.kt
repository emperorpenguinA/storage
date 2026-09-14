package com.mementostorage.app.domain.repository

import com.mementostorage.app.domain.model.Entry
import kotlinx.coroutines.flow.Flow

interface EntryRepository {
    fun observeEntries(libraryId: String): Flow<List<Entry>>
    suspend fun getEntry(id: String): Entry?
    suspend fun createEntry(libraryId: String, values: Map<String, String>): Entry
    suspend fun updateEntry(id: String, values: Map<String, String>)
    suspend fun deleteEntry(id: String)
    suspend fun getAllEntries(libraryIds: List<String>): List<Entry>

    /** Inserts or overwrites an entry at its existing id. Used by SyncService when restoring a backup. */
    suspend fun upsertEntry(entry: Entry)
}
