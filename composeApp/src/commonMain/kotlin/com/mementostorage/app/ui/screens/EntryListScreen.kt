package com.mementostorage.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mementostorage.app.di.AppContainer
import com.mementostorage.app.domain.model.Entry
import com.mementostorage.app.domain.model.FieldType
import com.mementostorage.app.domain.model.Library
import com.mementostorage.app.domain.model.LibraryField
import com.mementostorage.app.ui.components.PhotoThumbnail
import com.mementostorage.app.ui.components.SortCriterion
import com.mementostorage.app.ui.components.SortDirection
import com.mementostorage.app.ui.components.SortMenuButton
import com.mementostorage.app.ui.components.SortSpec
import com.mementostorage.app.ui.components.sortedBySpec

private const val PRIMARY_SORT_ID = "primary"
private const val UPDATED_SORT_ID = "updated"
private const val CREATED_SORT_ID = "created"
private const val FIELD_SORT_PREFIX = "field:"

/** A library's own fields, as additional sort criteria (sorting by an attachment id is meaningless, so PHOTO is skipped). */
private fun sortCriteriaFor(library: Library?): List<SortCriterion> {
    val fixed = listOf(
        SortCriterion(id = PRIMARY_SORT_ID, label = "名前順"),
        SortCriterion(id = UPDATED_SORT_ID, label = "更新日時順", defaultDirection = SortDirection.DESCENDING),
        SortCriterion(id = CREATED_SORT_ID, label = "作成日時順", defaultDirection = SortDirection.DESCENDING),
    )
    val fieldCriteria = library?.fields.orEmpty()
        .filter { it.type != FieldType.PHOTO }
        .sortedBy { it.position }
        .map { field -> SortCriterion(id = "$FIELD_SORT_PREFIX${field.id}", label = "「${field.name}」順") }
    return fixed + fieldCriteria
}

/** Comparator (always ascending; direction is applied separately) for [sortSpec]'s chosen criterion. */
private fun entryComparatorFor(sortSpec: SortSpec, library: Library?): Comparator<Entry> {
    val primaryFieldId = library?.fields?.minByOrNull { it.position }?.id
    val fieldId = sortSpec.criterionId.removePrefix(FIELD_SORT_PREFIX)
    val field = library?.fields?.firstOrNull { it.id == fieldId }
    return when {
        sortSpec.criterionId == UPDATED_SORT_ID -> compareBy { it.updatedAt }
        sortSpec.criterionId == CREATED_SORT_ID -> compareBy { it.createdAt }
        field != null -> field.toEntryComparator()
        else -> compareBy { entry: Entry -> primaryFieldId?.let { entry.values[it] }.orEmpty() }
    }
}

/** Sorts a field's raw stored string value the way it's actually meant to be compared, not lexicographically. */
private fun LibraryField.toEntryComparator(): Comparator<Entry> = when (type) {
    FieldType.NUMBER -> compareBy { entry: Entry -> entry.values[id]?.toDoubleOrNull() }
    FieldType.CURRENCY -> compareBy { entry: Entry -> entry.values[id]?.substringBefore('|')?.toDoubleOrNull() }
    FieldType.CHOICE -> compareBy { entry: Entry -> entry.values[id]?.let { options.indexOf(it).takeIf { idx -> idx >= 0 } } }
    else -> compareBy { entry: Entry -> entry.values[id] }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    container: AppContainer,
    libraryId: String,
    onBack: () -> Unit,
    onEditSchema: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onCreateEntry: () -> Unit,
) {
    var library by remember { mutableStateOf<Library?>(null) }
    LaunchedEffect(libraryId) { library = container.libraryRepository.getLibrary(libraryId) }

    val entries by container.entryRepository.observeEntries(libraryId).collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var sortSpec by remember { mutableStateOf(SortSpec(PRIMARY_SORT_ID, SortDirection.ASCENDING)) }
    val sortCriteria = remember(library) { sortCriteriaFor(library) }
    val filteredEntries = remember(entries, query, sortSpec, library) {
        val matching = if (query.isBlank()) {
            entries
        } else {
            entries.filter { entry -> entry.values.values.any { it.contains(query, ignoreCase = true) } }
        }
        matching.sortedBySpec(entryComparatorFor(sortSpec, library), sortSpec)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(library?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
                actions = {
                    SortMenuButton(criteria = sortCriteria, spec = sortSpec, onSpecChange = { sortSpec = it })
                    IconButton(onClick = onEditSchema) { Icon(Icons.Default.Settings, contentDescription = "項目を編集") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateEntry) {
                Icon(Icons.Default.Add, contentDescription = "新しいレコード")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("検索") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )

            val currentLibrary = library
            if (currentLibrary == null) {
                return@Scaffold
            }

            if (filteredEntries.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("レコードがありません。右下の + から追加しましょう。")
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        EntryRow(
                            container = container,
                            entry = entry,
                            library = currentLibrary,
                            onClick = { onOpenEntry(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(container: AppContainer, entry: Entry, library: Library, onClick: () -> Unit) {
    val sortedFields = library.fields.sortedBy { it.position }
    val primaryField = sortedFields.firstOrNull()
    val title = primaryField
        ?.let { field -> entry.values[field.id]?.let { field.formatValueForDisplay(it) } }
        ?.takeIf { it.isNotBlank() } ?: "(無題)"
    val subtitle = sortedFields.drop(1).take(2)
        .mapNotNull { field ->
            entry.values[field.id]?.takeIf { it.isNotBlank() }?.let { "${field.name}: ${field.formatValueForDisplay(it)}" }
        }
        .joinToString(" ・ ")
    val photoField = sortedFields.firstOrNull { it.type == FieldType.PHOTO }
    val photoAttachment = photoField
        ?.let { entry.values[it.id] }
        ?.let { attachmentId -> entry.attachments.firstOrNull { it.id == attachmentId } }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        ListItem(
            leadingContent = if (photoField != null) {
                { PhotoThumbnail(container = container, attachment = photoAttachment, enlargeOnLongClick = true, onClick = onClick) }
            } else null,
            headlineContent = { Text(title) },
            supportingContent = if (subtitle.isNotBlank()) {
                { Text(subtitle) }
            } else null,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        )
    }
}

/**
 * A CURRENCY field's raw value packs "amount|unit" into one string (see DynamicFieldInput's
 * CurrencyFieldInput) — render it as "amount unit" instead of the raw separator.
 */
private fun LibraryField.formatValueForDisplay(rawValue: String): String {
    if (type != FieldType.CURRENCY) return rawValue
    val separatorIndex = rawValue.lastIndexOf('|')
    return if (separatorIndex >= 0) {
        "${rawValue.substring(0, separatorIndex)} ${rawValue.substring(separatorIndex + 1)}"
    } else {
        rawValue
    }
}
