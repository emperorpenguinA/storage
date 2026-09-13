package com.mementostorage.app.domain.model

data class DriveAccountSettings(
    val accountEmail: String?,
    val rootFolderId: String?,
    val lastSyncAt: Long?,
) {
    val isConnected: Boolean get() = accountEmail != null

    companion object {
        val EMPTY = DriveAccountSettings(accountEmail = null, rootFolderId = null, lastSyncAt = null)
    }
}
