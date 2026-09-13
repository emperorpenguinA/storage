package com.mementostorage.app.data.repository

import com.mementostorage.app.data.local.WasmJsLocalStore
import com.mementostorage.app.data.snapshot.FieldDto
import com.mementostorage.app.data.snapshot.LibraryDto
import com.mementostorage.app.data.snapshot.toDomain
import com.mementostorage.app.data.snapshot.toDto
import com.mementostorage.app.domain.model.Library
import com.mementostorage.app.domain.model.LibraryField
import com.mementostorage.app.domain.repository.LibraryRepository
import com.mementostorage.app.util.IdGenerator
import com.mementostorage.app.util.nowEpochMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WasmJsLibraryRepository(private val store: WasmJsLocalStore) : LibraryRepository {

    override fun observeLibraries(): Flow<List<Library>> =
        store.snapshot.map { snapshot ->
            val fieldsByLibrary = snapshot.fields.groupBy { it.libraryId }
            snapshot.libraries
                .sortedByDescending { it.updatedAt }
                .map { it.toDomain(fieldsByLibrary[it.id].orEmpty()) }
        }

    override suspend fun getLibrary(id: String): Library? {
        val snapshot = store.snapshot.value
        val row = snapshot.libraries.firstOrNull { it.id == id } ?: return null
        val fields = snapshot.fields.filter { it.libraryId == id }.sortedBy { it.position }
        return row.toDomain(fields)
    }

    override suspend fun createLibrary(
        name: String,
        description: String,
        iconKey: String,
        fields: List<LibraryField>,
    ): Library {
        val libraryId = IdGenerator.newId()
        val now = nowEpochMillis()
        val fieldDtos = fields.mapIndexed { index, field ->
            field.copy(id = field.id.ifBlank { IdGenerator.newId() }, libraryId = libraryId, position = index).toDto()
        }
        store.update { snapshot ->
            snapshot.copy(
                libraries = snapshot.libraries + LibraryDto(libraryId, name, description, iconKey, now, now),
                fields = snapshot.fields + fieldDtos,
            )
        }
        return getLibrary(libraryId)!!
    }

    override suspend fun renameLibrary(id: String, name: String, description: String, iconKey: String) {
        store.update { snapshot ->
            snapshot.copy(
                libraries = snapshot.libraries.map {
                    if (it.id == id) it.copy(name = name, description = description, iconKey = iconKey, updatedAt = nowEpochMillis()) else it
                },
            )
        }
    }

    override suspend fun replaceFields(libraryId: String, fields: List<LibraryField>) {
        val fieldDtos = fields.mapIndexed { index, field ->
            field.copy(id = field.id.ifBlank { IdGenerator.newId() }, libraryId = libraryId, position = index).toDto()
        }
        store.update { snapshot ->
            snapshot.copy(
                fields = snapshot.fields.filterNot { it.libraryId == libraryId } + fieldDtos,
                libraries = snapshot.libraries.map {
                    if (it.id == libraryId) it.copy(updatedAt = nowEpochMillis()) else it
                },
            )
        }
    }

    override suspend fun deleteLibrary(id: String) {
        store.update { snapshot ->
            val entryIds = snapshot.entries.filter { it.libraryId == id }.map { it.id }.toSet()
            snapshot.copy(
                libraries = snapshot.libraries.filterNot { it.id == id },
                fields = snapshot.fields.filterNot { it.libraryId == id },
                entries = snapshot.entries.filterNot { it.libraryId == id },
                attachments = snapshot.attachments.filterNot { it.entryId in entryIds },
            )
        }
    }

    override suspend fun upsertLibrary(library: Library) {
        val libraryDto = LibraryDto(library.id, library.name, library.description, library.iconKey, library.createdAt, library.updatedAt)
        val fieldDtos = library.fields.mapIndexed { index, field ->
            field.copy(libraryId = library.id, position = index).toDto()
        }
        store.update { snapshot ->
            snapshot.copy(
                libraries = snapshot.libraries.filterNot { it.id == library.id } + libraryDto,
                fields = snapshot.fields.filterNot { it.libraryId == library.id } + fieldDtos,
            )
        }
    }
}

private fun LibraryDto.toDomain(fieldDtos: List<FieldDto>): Library = Library(
    id = id,
    name = name,
    description = description,
    iconKey = iconKey,
    fields = fieldDtos.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)
