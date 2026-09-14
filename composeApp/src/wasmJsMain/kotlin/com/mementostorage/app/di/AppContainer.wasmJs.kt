package com.mementostorage.app.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mementostorage.app.auth.GoogleAuthClient
import com.mementostorage.app.data.local.WasmJsAttachmentFileStore
import com.mementostorage.app.data.local.WasmJsLocalStore
import com.mementostorage.app.data.repository.WasmJsAttachmentRepository
import com.mementostorage.app.data.repository.WasmJsDriveSettingsRepository
import com.mementostorage.app.data.repository.WasmJsEntryRepository
import com.mementostorage.app.data.repository.WasmJsLibraryRepository
import com.mementostorage.app.drive.DriveApiClient
import com.mementostorage.app.drive.SyncService
import com.mementostorage.app.drive.createHttpClient

@Composable
actual fun rememberAppContainer(authClient: GoogleAuthClient): AppContainer {
    return remember {
        val store = WasmJsLocalStore()
        val libraryRepository = WasmJsLibraryRepository(store)
        val entryRepository = WasmJsEntryRepository(store)
        val attachmentRepository = WasmJsAttachmentRepository(store)
        val driveSettingsRepository = WasmJsDriveSettingsRepository(store)
        val attachmentFileStore = WasmJsAttachmentFileStore()
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
