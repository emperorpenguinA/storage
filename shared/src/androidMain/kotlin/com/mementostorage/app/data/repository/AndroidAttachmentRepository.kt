package com.mementostorage.app.data.repository

import com.mementostorage.app.db.AppDatabase
import com.mementostorage.app.db.Attachment as AttachmentRow
import com.mementostorage.app.domain.model.EntryAttachment
import com.mementostorage.app.domain.model.SyncState
import com.mementostorage.app.domain.repository.AttachmentRepository
import com.mementostorage.app.util.IdGenerator
import com.mementostorage.app.util.nowEpochMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAttachmentRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AttachmentRepository {

    override suspend fun getAttachmentsForEntry(entryId: String): List<EntryAttachment> =
        withContext(ioDispatcher) {
            database.attachmentQueries.selectAttachmentsByEntry(entryId).executeAsList().map { it.toDomain() }
        }

    override suspend fun getAttachment(id: String): EntryAttachment? = withContext(ioDispatcher) {
        database.attachmentQueries.selectAttachmentById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getPendingAttachments(): List<EntryAttachment> = withContext(ioDispatcher) {
        database.attachmentQueries.selectPendingAttachments().executeAsList().map { it.toDomain() }
    }

    override suspend fun addAttachment(
        entryId: String,
        fieldId: String,
        fileName: String,
        localPath: String,
        mimeType: String,
    ): EntryAttachment = withContext(ioDispatcher) {
        val id = IdGenerator.newId()
        database.attachmentQueries.insertAttachment(
            id = id,
            entryId = entryId,
            fieldId = fieldId,
            fileName = fileName,
            localPath = localPath,
            mimeType = mimeType,
            driveFileId = null,
            syncState = SyncState.PENDING.name,
            createdAt = nowEpochMillis(),
        )
        database.attachmentQueries.selectAttachmentById(id).executeAsOne().toDomain()
    }

    override suspend fun markSynced(id: String, driveFileId: String) {
        withContext(ioDispatcher) {
            database.attachmentQueries.updateAttachmentSync(
                driveFileId = driveFileId,
                syncState = SyncState.SYNCED.name,
                id = id,
            )
        }
    }

    override suspend fun markSyncState(id: String, state: SyncState) {
        withContext(ioDispatcher) {
            val current = database.attachmentQueries.selectAttachmentById(id).executeAsOneOrNull()
            database.attachmentQueries.updateAttachmentSync(
                driveFileId = current?.driveFileId,
                syncState = state.name,
                id = id,
            )
        }
    }

    override suspend fun deleteAttachment(id: String) {
        withContext(ioDispatcher) {
            database.attachmentQueries.deleteAttachmentById(id)
        }
    }

    override suspend fun upsertAttachment(attachment: EntryAttachment) {
        withContext(ioDispatcher) {
            database.attachmentQueries.upsertAttachment(
                id = attachment.id,
                entryId = attachment.entryId,
                fieldId = attachment.fieldId,
                fileName = attachment.fileName,
                localPath = attachment.localPath,
                mimeType = attachment.mimeType,
                driveFileId = attachment.driveFileId,
                syncState = attachment.syncState.name,
                createdAt = attachment.createdAt,
            )
        }
    }
}

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
