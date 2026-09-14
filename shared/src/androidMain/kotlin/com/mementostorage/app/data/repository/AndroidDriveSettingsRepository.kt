package com.mementostorage.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mementostorage.app.db.AppDatabase
import com.mementostorage.app.domain.model.DriveAccountSettings
import com.mementostorage.app.domain.repository.DriveSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AndroidDriveSettingsRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DriveSettingsRepository {

    override fun observeSettings(): Flow<DriveAccountSettings> =
        database.driveSettingsQueries.selectDriveSettings()
            .asFlow().mapToOneOrNull(ioDispatcher)
            .map { row ->
                if (row == null) {
                    DriveAccountSettings.EMPTY
                } else {
                    DriveAccountSettings(row.accountEmail, row.rootFolderId, row.lastSyncAt)
                }
            }

    override suspend fun save(settings: DriveAccountSettings) {
        withContext(ioDispatcher) {
            database.driveSettingsQueries.upsertDriveSettings(
                accountEmail = settings.accountEmail,
                rootFolderId = settings.rootFolderId,
                lastSyncAt = settings.lastSyncAt,
            )
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            database.driveSettingsQueries.clearDriveSettings()
        }
    }
}
