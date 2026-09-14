package com.mementostorage.app.domain.model

/**
 * A user-defined "database" (called a library, as in Memento Database): a name, an icon,
 * and a schema made of [fields]. Each [Entry] belonging to this library stores one value
 * per field.
 */
data class Library(
    val id: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val fields: List<LibraryField>,
    val createdAt: Long,
    val updatedAt: Long,
)
