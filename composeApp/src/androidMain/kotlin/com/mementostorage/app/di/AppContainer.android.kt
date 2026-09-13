package com.mementostorage.app.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mementostorage.app.auth.GoogleAuthClient
import com.mementostorage.app.data.db.DatabaseDriverFactory
import com.mementostorage.app.data.local.AndroidAttachmentFileStore
import com.mementostorage.app.data.repository.AndroidAttachmentRepository
import com.mementostorage.app.data.repository.AndroidDriveSettingsRepository
import com.mementostorage.app.data.repository.AndroidEntryRepository
import com.mementostorage.app.data.repository.AndroidLibraryRepository
import com.mementostorage.app.db.AppDatabase
import com.mementostorage.app.drive.DriveApiClient
import com.mementostorage.app.drive.SyncService
import com.mementostorage.app.drive.createHttpClient

@Composable
actual fun rememberAppContainer(authClient: GoogleAuthClient): AppContainer {
    val appContext = LocalContext.current.applicationContext
    return remember {
        val database = AppDatabase(DatabaseDriverFactory(appContext).createDriver())
        val libraryRepository = AndroidLibraryRepository(database)
        val entryRepository = AndroidEntryRepository(database)
        val attachmentRepository = AndroidAttachmentRepository(database)
        val driveSettingsRepository = AndroidDriveSettingsRepository(database)
        val attachmentFileStore = AndroidAttachmentFileStore(appContext)
        val driveApiClient = DriveApiClient(createHttpClient(), authClient)
        val syncService = SyncService(
            driveApiClient = driveApiClient,
            fileStore = attachmentFileStore,
            libraryRepository = libraryRepository,
            entryRepository = entryRepository,
            attachmentRepository = attachmentRepository,
            driveSettingsRepository = driveSettingsRepository,
        )
        AppContainer(
            libraryRepository = libraryRepository,
            entryRepository = entryRepository,
            attachmentRepository = attachmentRepository,
            driveSettingsRepository = driveSettingsRepository,
            attachmentFileStore = attachmentFileStore,
            syncService = syncService,
        )
    }
}
