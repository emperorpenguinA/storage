package com.mementostorage.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mementostorage.app.db.AppDatabase
import com.mementostorage.app.db.Attachment as AttachmentRow
import com.mementostorage.app.db.Entry as EntryRow
import com.mementostorage.app.domain.model.Entry
import com.mementostorage.app.domain.model.EntryAttachment
import com.mementostorage.app.domain.model.SyncState
import com.mementostorage.app.domain.repository.EntryRepository
import com.mementostorage.app.util.IdGenerator
import com.mementostorage.app.util.nowEpochMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidEntryRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EntryRepository {

    override fun observeEntries(libraryId: String): Flow<List<Entry>> {
        val entries = database.entryQueries.selectEntriesByLibrary(libraryId)
            .asFlow().mapToList(ioDispatcher)
        val attachments = database.attachmentQueries.selectAttachmentsByLibrary(libraryId)
            .asFlow().mapToList(ioDispatcher)
        return combine(entries, attachments) { entryRows, attachmentRows ->
            val attachmentsByEntry = attachmentRows.groupBy { it.entryId }
            entryRows.map { it.toDomain(attachmentsByEntry[it.id].orEmpty()) }
        }
    }

    override suspend fun getEntry(id: String): Entry? = withContext(ioDispatcher) {
        val row = database.entryQueries.selectEntryById(id).executeAsOneOrNull() ?: return@withContext null
        val attachments = database.attachmentQueries.selectAttachmentsByEntry(id).executeAsList()
        row.toDomain(attachments)
    }

    override suspend fun createEntry(libraryId: String, values: Map<String, String>): Entry =
        withContext(ioDispatcher) {
            val id = IdGenerator.newId()
            val now = nowEpochMillis()
            database.entryQueries.insertEntry(
                id = id,
                libraryId = libraryId,
                valuesJson = Json.encodeToString(values),
                createdAt = now,
                updatedAt = now,
            )
            getEntry(id)!!
        }

    override suspend fun updateEntry(id: String, values: Map<String, String>) {
        withContext(ioDispatcher) {
            database.entryQueries.updateEntry(
                valuesJson = Json.encodeToString(values),
                updatedAt = nowEpochMillis(),
                id = id,
            )
        }
    }

    override suspend fun deleteEntry(id: String) {
        withContext(ioDispatcher) {
            database.entryQueries.deleteEntryById(id)
        }
    }

    override suspend fun getAllEntries(libraryIds: List<String>): List<Entry> = withContext(ioDispatcher) {
        if (libraryIds.isEmpty()) return@withContext emptyList()
        val entryRows = database.entryQueries.selectEntriesByLibraries(libraryIds).executeAsList()
        val attachmentRows = database.attachmentQueries.selectAttachmentsByEntries(entryRows.map { it.id }).executeAsList()
        val attachmentsByEntry = attachmentRows.groupBy { it.entryId }
        entryRows.map { it.toDomain(attachmentsByEntry[it.id].orEmpty()) }
    }

    override suspend fun upsertEntry(entry: Entry) {
        withContext(ioDispatcher) {
            database.entryQueries.upsertEntry(
                id = entry.id,
                libraryId = entry.libraryId,
                valuesJson = Json.encodeToString(entry.values),
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
            )
        }
    }
}

private fun EntryRow.toDomain(attachmentRows: List<AttachmentRow>): Entry = Entry(
    id = id,
    libraryId = libraryId,
    values = runCatching { Json.decodeFromString<Map<String, String>>(valuesJson) }.getOrDefault(emptyMap()),
    attachments = attachmentRows.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun AttachmentRow.toDomain(): EntryAttachment = EntryAttachment(
    id = id,
    entryId = entryId,
    fieldId = fieldId,
    fileName = fileName,
    localPath = localPath,
    mimeType = mimeType,
    driveFileId = driveFileId,
    syncState = SyncState.fromStorageName(syncState),
    createdAt = createdAt,
)
