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
import com.mementostorage.app.ui.components.SortMenuButton
import com.mementostorage.app.ui.components.SortOption

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
    var sortOption by remember { mutableStateOf(SortOption.NAME) }
    val filteredEntries = remember(entries, query, sortOption, library) {
        val matching = if (query.isBlank()) {
            entries
        } else {
            entries.filter { entry -> entry.values.values.any { it.contains(query, ignoreCase = true) } }
        }
        val primaryFieldId = library?.fields?.minByOrNull { it.position }?.id
        when (sortOption) {
            SortOption.NAME -> matching.sortedBy { entry -> primaryFieldId?.let { entry.values[it] }.orEmpty() }
            SortOption.UPDATED_DESC -> matching.sortedByDescending { it.updatedAt }
            SortOption.CREATED_DESC -> matching.sortedByDescending { it.createdAt }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(library?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
                actions = {
                    SortMenuButton(selected = sortOption, onSelect = { sortOption = it })
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
                { PhotoThumbnail(container = container, attachment = photoAttachment) }
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
