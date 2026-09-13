package com.mementostorage.app.domain.repository

import com.mementostorage.app.domain.model.EntryAttachment
import com.mementostorage.app.domain.model.SyncState

interface AttachmentRepository {
    suspend fun getAttachmentsForEntry(entryId: String): List<EntryAttachment>
    suspend fun getAttachment(id: String): EntryAttachment?
    suspend fun getPendingAttachments(): List<EntryAttachment>

    suspend fun addAttachment(
        entryId: String,
        fieldId: String,
        fileName: String,
        localPath: String,
        mimeType: String,
    ): EntryAttachment

    suspend fun markSynced(id: String, driveFileId: String)
    suspend fun markSyncState(id: String, state: SyncState)
    suspend fun deleteAttachment(id: String)

    /** Inserts or overwrites attachment metadata at its existing id. Used by SyncService when restoring a backup. */
    suspend fun upsertAttachment(attachment: EntryAttachment)
}
