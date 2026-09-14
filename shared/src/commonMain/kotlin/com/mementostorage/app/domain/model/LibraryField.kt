package com.mementostorage.app.domain.model

/**
 * One column of a [Library]'s user-defined schema.
 * [options] only applies to [FieldType.CHOICE].
 */
data class LibraryField(
    val id: String,
    val libraryId: String,
    val name: String,
    val type: FieldType,
    val position: Int,
    val isRequired: Boolean,
    val options: List<String> = emptyList(),
)
