package com.mementostorage.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mementostorage.app.di.AppContainer
import com.mementostorage.app.domain.model.EntryAttachment
import com.mementostorage.app.domain.model.Library
import com.mementostorage.app.ui.components.DynamicFieldInput
import kotlinx.coroutines.launch

/**
 * A new entry is saved (empty) as soon as this screen opens, then edited in place — see the
 * class doc on DynamicFieldInput for why a PHOTO field needs an entry id to attach to before
 * the user has typed anything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    container: AppContainer,
    libraryId: String,
    entryId: String?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf<Library?>(null) }
    var resolvedEntryId by remember { mutableStateOf(entryId) }
    var values by remember { mutableStateOf(mapOf<String, String>()) }
    var attachments by remember { mutableStateOf(listOf<EntryAttachment>()) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(libraryId, entryId) {
        library = container.libraryRepository.getLibrary(libraryId)
        val id = entryId ?: container.entryRepository.createEntry(libraryId, emptyMap()).id
        resolvedEntryId = id
        val entry = container.entryRepository.getEntry(id)
        if (entry != null) {
            values = entry.values
            attachments = entry.attachments
        }
        ready = true
    }

    suspend fun reloadAttachments() {
        val id = resolvedEntryId ?: return
        attachments = container.attachmentRepository.getAttachmentsForEntry(id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entryId == null) "新しいレコード" else "レコードを編集") },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            resolvedEntryId?.let { container.entryRepository.updateEntry(it, values) }
                            onDone()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "保存して戻る")
                    }
                },
            )
        },
    ) { padding ->
        if (!ready) {
            CircularProgressIndicator(modifier = Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        val currentLibrary = library ?: return@Scaffold
        val currentEntryId = resolvedEntryId ?: return@Scaffold

        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            currentLibrary.fields.sortedBy { it.position }.forEach { field ->
                DynamicFieldInput(
                    field = field,
                    value = values[field.id].orEmpty(),
                    onValueChange = { newValue ->
                        values = values + (field.id to newValue)
                        scope.launch { container.entryRepository.updateEntry(currentEntryId, values) }
                    },
                    container = container,
                    entryId = currentEntryId,
                    attachments = attachments,
                    onAttachmentsChanged = { scope.launch { reloadAttachments() } },
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        container.entryRepository.updateEntry(currentEntryId, values)
                        onDone()
                    }
                }) {
                    Text("保存")
                }
                TextButton(onClick = {
                    scope.launch {
                        container.entryRepository.deleteEntry(currentEntryId)
                        onDone()
                    }
                }) {
                    Text("このレコードを削除")
                }
            }
        }
    }
}
