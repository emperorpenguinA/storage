package com.mementostorage.app.domain.repository

import com.mementostorage.app.domain.model.DriveAccountSettings
import kotlinx.coroutines.flow.Flow

interface DriveSettingsRepository {
    fun observeSettings(): Flow<DriveAccountSettings>
    suspend fun save(settings: DriveAccountSettings)
    suspend fun clear()
}
