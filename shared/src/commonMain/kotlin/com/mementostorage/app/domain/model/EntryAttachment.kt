package com.mementostorage.app.domain.model

/**
 * A photo or file attached to one [FieldType.PHOTO] field of an [Entry].
 * [localPath] is a platform-specific reference to the on-device copy (a file path on
 * Android, an object-store key on web); [driveFileId] is set once it has been uploaded.
 */
data class EntryAttachment(
    val id: String,
    val entryId: String,
    val fieldId: String,
    val fileName: String,
    val localPath: String,
    val mimeType: String,
    val driveFileId: String?,
    val syncState: SyncState,
    val createdAt: Long,
)
