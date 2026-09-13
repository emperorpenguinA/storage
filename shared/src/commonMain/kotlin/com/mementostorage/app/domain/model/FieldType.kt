package com.mementostorage.app.domain.model

/**
 * The kinds of columns a user can add when designing a [Library]'s schema.
 * Stored as its [name] in the database, so renaming an entry here requires a migration.
 */
enum class FieldType {
    TEXT,
    LONG_TEXT,
    NUMBER,
    DATE,
    BOOLEAN,
    CHOICE,
    LINK,
    PHOTO;

    companion object {
        fun fromStorageName(name: String): FieldType =
            entries.firstOrNull { it.name == name } ?: TEXT
    }
}
