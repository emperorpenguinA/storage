package com.mementostorage.app.domain.model

enum class SyncState {
    PENDING,
    SYNCED,
    FAILED;

    companion object {
        fun fromStorageName(name: String): SyncState =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}
