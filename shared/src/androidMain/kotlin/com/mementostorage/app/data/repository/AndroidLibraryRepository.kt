package com.mementostorage.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mementostorage.app.db.AppDatabase
import com.mementostorage.app.db.Field as FieldRow
import com.mementostorage.app.db.Library as LibraryRow
import com.mementostorage.app.domain.model.FieldType
import com.mementostorage.app.domain.model.Library
import com.mementostorage.app.domain.model.LibraryField
import com.mementostorage.app.domain.repository.LibraryRepository
import com.mementostorage.app.util.IdGenerator
import com.mementostorage.app.util.nowEpochMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidLibraryRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibraryRepository {

    override fun observeLibraries(): Flow<List<Library>> {
        val libraries = database.libraryQueries.selectAllLibraries()
            .asFlow().mapToList(ioDispatcher)
        val fields = database.fieldQueries.selectAllFields()
            .asFlow().mapToList(ioDispatcher)
        return combine(libraries, fields) { libraryRows, fieldRows ->
            val fieldsByLibrary = fieldRows.groupBy { it.libraryId }
            libraryRows.map { it.toDomain(fieldsByLibrary[it.id].orEmpty()) }
        }
    }

    override suspend fun getLibrary(id: String): Library? = withContext(ioDispatcher) {
        val row = database.libraryQueries.selectLibraryById(id).executeAsOneOrNull() ?: return@withContext null
        val fields = database.fieldQueries.selectFieldsByLibrary(id).executeAsList()
        row.toDomain(fields)
    }

    override suspend fun createLibrary(
        name: String,
        description: String,
        iconKey: String,
        fields: List<LibraryField>,
    ): Library = withContext(ioDispatcher) {
        val libraryId = IdGenerator.newId()
        val now = nowEpochMillis()
        database.transaction {
            database.libraryQueries.insertLibrary(
                id = libraryId,
                name = name,
                description = description,
                iconKey = iconKey,
                createdAt = now,
                updatedAt = now,
            )
            fields.forEachIndexed { index, field ->
                database.fieldQueries.insertField(
                    id = field.id.ifBlank { IdGenerator.newId() },
                    libraryId = libraryId,
                    name = field.name,
                    type = field.type.name,
                    position = index.toLong(),
                    isRequired = if (field.isRequired) 1L else 0L,
                    optionsJson = Json.encodeToString(field.options),
                )
            }
        }
        getLibrary(libraryId)!!
    }

    override suspend fun renameLibrary(id: String, name: String, description: String, iconKey: String) {
        withContext(ioDispatcher) {
            database.libraryQueries.updateLibrary(
                name = name,
                description = description,
                iconKey = iconKey,
                updatedAt = nowEpochMillis(),
                id = id,
            )
        }
    }

    override suspend fun replaceFields(libraryId: String, fields: List<LibraryField>) {
        withContext(ioDispatcher) {
            database.transaction {
                database.fieldQueries.deleteFieldsByLibrary(libraryId)
                fields.forEachIndexed { index, field ->
                    database.fieldQueries.insertField(
                        id = field.id.ifBlank { IdGenerator.newId() },
                        libraryId = libraryId,
                        name = field.name,
                        type = field.type.name,
                        position = index.toLong(),
                        isRequired = if (field.isRequired) 1L else 0L,
                        optionsJson = Json.encodeToString(field.options),
                    )
                }
                database.libraryQueries.touchLibrary(updatedAt = nowEpochMillis(), id = libraryId)
            }
        }
    }

    override suspend fun deleteLibrary(id: String) {
        withContext(ioDispatcher) {
            database.libraryQueries.deleteLibraryById(id)
        }
    }

    override suspend fun upsertLibrary(library: Library) {
        withContext(ioDispatcher) {
            database.transaction {
                database.libraryQueries.upsertLibrary(
                    id = library.id,
                    name = library.name,
                    description = library.description,
                    iconKey = library.iconKey,
                    createdAt = library.createdAt,
                    updatedAt = library.updatedAt,
                )
                database.fieldQueries.deleteFieldsByLibrary(library.id)
                library.fields.forEachIndexed { index, field ->
                    database.fieldQueries.insertField(
                        id = field.id.ifBlank { IdGenerator.newId() },
                        libraryId = library.id,
                        name = field.name,
                        type = field.type.name,
                        position = index.toLong(),
                        isRequired = if (field.isRequired) 1L else 0L,
                        optionsJson = Json.encodeToString(field.options),
                    )
                }
            }
        }
    }
}

private fun LibraryRow.toDomain(fieldRows: List<FieldRow>): Library = Library(
    id = id,
    name = name,
    description = description,
    iconKey = iconKey,
    fields = fieldRows.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun FieldRow.toDomain(): LibraryField = LibraryField(
    id = id,
    libraryId = libraryId,
    name = name,
    type = FieldType.fromStorageName(type),
    position = position.toInt(),
    isRequired = isRequired != 0L,
    options = runCatching { Json.decodeFromString<List<String>>(optionsJson) }.getOrDefault(emptyList()),
)
