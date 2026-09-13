package com.mementostorage.app.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.mementostorage.app.db.AppDatabase

/** Android-only: local storage here is a plain on-device SQLite file via SQLDelight. */
class DatabaseDriverFactory(private val context: Context) {
    fun createDriver(): SqlDriver =
        AndroidSqliteDriver(AppDatabase.Schema, context, "memento_storage.db")
}
