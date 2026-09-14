package com.mementostorage.app.data.repository

import com.mementostorage.app.data.local.WasmJsLocalStore
import com.mementostorage.app.data.snapshot.AttachmentDto
import com.mementostorage.app.data.snapshot.toDomain
import com.mementostorage.app.domain.model.EntryAttachment
import com.mementostorage.app.domain.model.SyncState
import com.mementostorage.app.domain.repository.AttachmentRepository
import com.mementostorage.app.util.IdGenerator
import com.mementostorage.app.util.nowEpochMillis

class WasmJsAttachmentRepository(private val store: WasmJsLocalStore) : AttachmentRepository {

    override suspend fun getAttachmentsForEntry(entryId: String): List<EntryAttachment> =
        store.snapshot.value.attachments.filter { it.entryId == entryId }.map { it.toDomain() }

    override suspend fun getAttachment(id: String): EntryAttachment? =
        store.snapshot.value.attachments.firstOrNull { it.id == id }?.toDomain()

    override suspend fun getPendingAttachments(): List<EntryAttachment> =
        store.snapshot.value.attachments.filter { it.syncState != SyncState.SYNCED.name }.map { it.toDomain() }

    override suspend fun addAttachment(
        entryId: String,
        fieldId: String,
        fileName: String,
        localPath: String,
        mimeType: String,
    ): EntryAttachment {
        val attachment = AttachmentDto(
            id = IdGenerator.newId(),
            entryId = entryId,
            fieldId = fieldId,
            fileName = fileName,
            localPath = localPath,
            mimeType = mimeType,
            driveFileId = null,
            syncState = SyncState.PENDING.name,
            createdAt = nowEpochMillis(),
        )
        store.update { snapshot -> snapshot.copy(attachments = snapshot.attachments + attachment) }
        return attachment.toDomain()
    }

    override suspend fun markSynced(id: String, driveFileId: String) {
        store.update { snapshot ->
            snapshot.copy(
                attachments = snapshot.attachments.map {
                    if (it.id == id) it.copy(driveFileId = driveFileId, syncState = SyncState.SYNCED.name) else it
                },
            )
        }
    }

    override suspend fun markSyncState(id: String, state: SyncState) {
        store.update { snapshot ->
            snapshot.copy(
                attachments = snapshot.attachments.map {
                    if (it.id == id) it.copy(syncState = state.name) else it
                },
            )
        }
    }

    override suspend fun deleteAttachment(id: String) {
        store.update { snapshot -> snapshot.copy(attachments = snapshot.attachments.filterNot { it.id == id }) }
    }

    override suspend fun upsertAttachment(attachment: EntryAttachment) {
        val dto = AttachmentDto(
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
        store.update { snapshot -> snapshot.copy(attachments = snapshot.attachments.filterNot { it.id == attachment.id } + dto) }
    }
}
