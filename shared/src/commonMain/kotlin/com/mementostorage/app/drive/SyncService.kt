package com.mementostorage.app.drive

import com.mementostorage.app.data.local.AttachmentFileStore
import com.mementostorage.app.data.snapshot.AppDataSnapshot
import com.mementostorage.app.data.snapshot.toDomain
import com.mementostorage.app.data.snapshot.toDto
import com.mementostorage.app.domain.repository.AttachmentRepository
import com.mementostorage.app.domain.repository.DriveSettingsRepository
import com.mementostorage.app.domain.repository.EntryRepository
import com.mementostorage.app.domain.repository.LibraryRepository
import com.mementostorage.app.util.nowEpochMillis
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private const val BACKUP_FILE_NAME = "backup.json"

/**
 * Backs up the local database (libraries/fields/entries/attachment metadata, as one JSON
 * document) and any not-yet-uploaded attachment bytes to the user's "MementoStorageApp"
 * Drive folder, and can pull that same snapshot back down onto a new device.
 *
 * This is a manual, whole-snapshot sync (no per-field conflict resolution): the side that
 * backs up last wins for any library/entry both sides touched. That is enough for "my data
 * follows me between one phone and the web app", which is what was asked for; a real
 * two-way merge would be a substantial follow-up.
 */
class SyncService(
    private val driveApiClient: DriveApiClient,
    private val fileStore: AttachmentFileStore,
    private val libraryRepository: LibraryRepository,
    private val entryRepository: EntryRepository,
    private val attachmentRepository: AttachmentRepository,
    private val driveSettingsRepository: DriveSettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun backupNow(): Result<Unit> = runCatching {
        val folderId = driveApiClient.ensureAppFolder()

        val libraries = libraryRepository.observeLibraries().first()
        val entries = entryRepository.getAllEntries(libraries.map { it.id })

        val snapshot = AppDataSnapshot(
            libraries = libraries.map { it.toDto() },
            fields = libraries.flatMap { it.fields }.map { it.toDto() },
            entries = entries.map { it.toDto() },
            attachments = entries.flatMap { it.attachments }.map { it.toDto() },
        )

        val existingBackup = driveApiClient.findFileByName(folderId, BACKUP_FILE_NAME)
        driveApiClient.uploadText(
            parentId = folderId,
            fileName = BACKUP_FILE_NAME,
            mimeType = "application/json",
            content = json.encodeToString(AppDataSnapshot.serializer(), snapshot),
            existingFileId = existingBackup?.id,
        )

        attachmentRepository.getPendingAttachments().forEach { attachment ->
            val bytes = fileStore.readBytes(attachment.localPath)
            if (bytes != null) {
                val uploaded = driveApiClient.uploadBytes(folderId, attachment.fileName, attachment.mimeType, bytes)
                attachmentRepository.markSynced(attachment.id, uploaded.id)
            }
        }

        val currentSettings = driveSettingsRepository.observeSettings().first()
        driveSettingsRepository.save(currentSettings.copy(rootFolderId = folderId, lastSyncAt = nowEpochMillis()))
    }

    /**
     * Pulls the latest backup down and upserts it into local storage. Attachment binary
     * content is not re-downloaded eagerly here — only the metadata row (including the
     * driveFileId) is restored; [DriveApiClient.downloadBytes] can fetch the actual bytes the
     * first time a restored photo is opened.
     */
    suspend fun restoreLatestBackup(): Result<Boolean> = runCatching {
        val folderId = driveApiClient.ensureAppFolder()
        val backupFile = driveApiClient.findFileByName(folderId, BACKUP_FILE_NAME) ?: return@runCatching false

        val snapshot = json.decodeFromString(AppDataSnapshot.serializer(), driveApiClient.downloadText(backupFile.id))
        val fieldsByLibrary = snapshot.fields.groupBy { it.libraryId }

        snapshot.libraries.forEach { libraryDto ->
            libraryRepository.upsertLibrary(
                libraryDto.toDomain(fieldsByLibrary[libraryDto.id].orEmpty().map { it.toDomain() }),
            )
        }
        snapshot.entries.forEach { entryDto ->
            entryRepository.upsertEntry(entryDto.toDomain(emptyList()))
        }
        snapshot.attachments.forEach { attachmentDto ->
            attachmentRepository.upsertAttachment(attachmentDto.toDomain())
        }

        val currentSettings = driveSettingsRepository.observeSettings().first()
        driveSettingsRepository.save(currentSettings.copy(rootFolderId = folderId, lastSyncAt = nowEpochMillis()))
        true
    }
}
