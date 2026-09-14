package com.mementostorage.app.domain.repository

import com.mementostorage.app.domain.model.Library
import com.mementostorage.app.domain.model.LibraryField
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeLibraries(): Flow<List<Library>>
    suspend fun getLibrary(id: String): Library?

    /** Creates a library together with its initial field list in one transaction. */
    suspend fun createLibrary(name: String, description: String, iconKey: String, fields: List<LibraryField>): Library

    suspend fun renameLibrary(id: String, name: String, description: String, iconKey: String)

    /** Replaces the full field list of a library (add/remove/reorder), keeping field ids stable where reused. */
    suspend fun replaceFields(libraryId: String, fields: List<LibraryField>)

    suspend fun deleteLibrary(id: String)

    /** Inserts or overwrites a library (and its fields) at its existing id. Used by SyncService when restoring a backup. */
    suspend fun upsertLibrary(library: Library)
}
