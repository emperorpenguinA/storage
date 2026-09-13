package com.mementostorage.app.di

import androidx.compose.runtime.Composable
import com.mementostorage.app.auth.GoogleAuthClient
import com.mementostorage.app.data.local.AttachmentFileStore
import com.mementostorage.app.domain.repository.AttachmentRepository
import com.mementostorage.app.domain.repository.DriveSettingsRepository
import com.mementostorage.app.domain.repository.EntryRepository
import com.mementostorage.app.domain.repository.LibraryRepository
import com.mementostorage.app.drive.SyncService

/** Everything a screen needs, built once per app launch and handed down via composition. */
class AppContainer(
    val libraryRepository: LibraryRepository,
    val entryRepository: EntryRepository,
    val attachmentRepository: AttachmentRepository,
    val driveSettingsRepository: DriveSettingsRepository,
    val attachmentFileStore: AttachmentFileStore,
    val syncService: SyncService,
)

/** Wires up the platform-specific repository implementations described in shared/build.gradle.kts. */
@Composable
expect fun rememberAppContainer(authClient: GoogleAuthClient): AppContainer
