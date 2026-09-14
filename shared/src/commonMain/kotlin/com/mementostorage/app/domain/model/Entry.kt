package com.mementostorage.app.domain.model

/**
 * One row of a [Library]. [values] maps [LibraryField.id] to the raw stored value: the
 * literal text for TEXT/LONG_TEXT/LINK/CHOICE, an epoch-day string for DATE, "true"/"false"
 * for BOOLEAN, and an [EntryAttachment.id] for PHOTO (looked up in [attachments]).
 */
data class Entry(
    val id: String,
    val libraryId: String,
    val values: Map<String, String>,
    val attachments: List<EntryAttachment> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)
