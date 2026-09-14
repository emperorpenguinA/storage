package com.mementostorage.app.data.snapshot

import com.mementostorage.app.domain.model.DriveAccountSettings
import com.mementostorage.app.domain.model.Entry
import com.mementostorage.app.domain.model.EntryAttachment
import com.mementostorage.app.domain.model.FieldType
import com.mementostorage.app.domain.model.Library
import com.mementostorage.app.domain.model.LibraryField
import com.mementostorage.app.domain.model.SyncState
import kotlinx.serialization.Serializable

/**
 * The full, portable representation of a user's data.
 *
 * This single JSON shape serves two purposes: it is the document the wasmJs target keeps in
 * `localStorage` (see WasmJsLocalStore), and it is what [com.mementostorage.app.drive.SyncService]
 * uploads to/downloads from Google Drive as `backup.json` so a library created on one device
 * shows up on another after a restore.
 */
@Serializable
data class AppDataSnapshot(
    val libraries: List<LibraryDto> = emptyList(),
    val fields: List<FieldDto> = emptyList(),
    val entries: List<EntryDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
    val driveSettings: DriveSettingsDto? = null,
) {
    companion object {
        val EMPTY = AppDataSnapshot()
    }
}

@Serializable
data class LibraryDto(
    val id: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class FieldDto(
    val id: String,
    val libraryId: String,
    val name: String,
    val type: String,
    val position: Int,
    val isRequired: Boolean,
    val options: List<String> = emptyList(),
)

@Serializable
data class EntryDto(
    val id: String,
    val libraryId: String,
    val values: Map<String, String>,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AttachmentDto(
    val id: String,
    val entryId: String,
    val fieldId: String,
    val fileName: String,
    val localPath: String,
    val mimeType: String,
    val driveFileId: String?,
    val syncState: String,
    val createdAt: Long,
)

@Serializable
data class DriveSettingsDto(
    val accountEmail: String?,
    val rootFolderId: String?,
    val lastSyncAt: Long?,
)

fun LibraryDto.toDomain(fields: List<LibraryField> = emptyList()): Library = Library(
    id = id,
    name = name,
    description = description,
    iconKey = iconKey,
    fields = fields,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Library.toDto(): LibraryDto = LibraryDto(
    id = id,
    name = name,
    description = description,
    iconKey = iconKey,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FieldDto.toDomain(): LibraryField = LibraryField(
    id = id,
    libraryId = libraryId,
    name = name,
    type = FieldType.fromStorageName(type),
    position = position,
    isRequired = isRequired,
    options = options,
)

fun LibraryField.toDto(): FieldDto = FieldDto(
    id = id,
    libraryId = libraryId,
    name = name,
    type = type.name,
    position = position,
    isRequired = isRequired,
    options = options,
)

fun EntryDto.toDomain(attachments: List<EntryAttachment>): Entry = Entry(
    id = id,
    libraryId = libraryId,
    values = values,
    attachments = attachments,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Entry.toDto(): EntryDto = EntryDto(
    id = id,
    libraryId = libraryId,
    values = values,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AttachmentDto.toDomain(): EntryAttachment = EntryAttachment(
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

fun EntryAttachment.toDto(): AttachmentDto = AttachmentDto(
    id = id,
    entryId = entryId,
    fieldId = fieldId,
    fileName = fileName,
    localPath = localPath,
    mimeType = mimeType,
    driveFileId = driveFileId,
    syncState = syncState.name,
    createdAt = createdAt,
)

fun DriveSettingsDto.toDomain(): DriveAccountSettings = DriveAccountSettings(
    accountEmail = accountEmail,
    rootFolderId = rootFolderId,
    lastSyncAt = lastSyncAt,
)

fun DriveAccountSettings.toDto(): DriveSettingsDto = DriveSettingsDto(
    accountEmail = accountEmail,
    rootFolderId = rootFolderId,
    lastSyncAt = lastSyncAt,
)
