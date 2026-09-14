package com.mementostorage.app.data.repository

import com.mementostorage.app.data.local.WasmJsLocalStore
import com.mementostorage.app.data.snapshot.toDomain
import com.mementostorage.app.data.snapshot.toDto
import com.mementostorage.app.domain.model.DriveAccountSettings
import com.mementostorage.app.domain.repository.DriveSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WasmJsDriveSettingsRepository(private val store: WasmJsLocalStore) : DriveSettingsRepository {

    override fun observeSettings(): Flow<DriveAccountSettings> =
        store.snapshot.map { it.driveSettings?.toDomain() ?: DriveAccountSettings.EMPTY }

    override suspend fun save(settings: DriveAccountSettings) {
        store.update { snapshot -> snapshot.copy(driveSettings = settings.toDto()) }
    }

    override suspend fun clear() {
        store.update { snapshot -> snapshot.copy(driveSettings = null) }
    }
}
