package com.mementostorage.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mementostorage.app.di.AppContainer
import com.mementostorage.app.domain.model.FieldType
import com.mementostorage.app.domain.model.LibraryField
import com.mementostorage.app.util.IdGenerator
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private data class FieldDraft(
    val id: String,
    val name: String,
    val type: FieldType,
    val isRequired: Boolean,
    val optionsText: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryEditorScreen(
    container: AppContainer,
    libraryId: String?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf(listOf<FieldDraft>()) }
    var loaded by remember { mutableStateOf(libraryId == null) }

    LaunchedEffect(libraryId) {
        if (libraryId != null) {
            val library = container.libraryRepository.getLibrary(libraryId)
            if (library != null) {
                name = library.name
                description = library.description
                fields = library.fields.sortedBy { it.position }.map {
                    FieldDraft(it.id, it.name, it.type, it.isRequired, it.options.joinToString(", "))
                }
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (libraryId == null) "新しいライブラリ" else "ライブラリを編集") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("ライブラリ名") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("説明（任意）") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Text("項目", style = MaterialTheme.typography.titleMedium)

            fields.forEachIndexed { index, field ->
                FieldDraftRow(
                    field = field,
                    onChange = { updated -> fields = fields.toMutableList().also { it[index] = updated } },
                    onRemove = { fields = fields.toMutableList().also { it.removeAt(index) } },
                )
            }

            TextButton(onClick = {
                fields = fields + FieldDraft(IdGenerator.newId(), "", FieldType.TEXT, false, "")
            }) {
                Text("+ 項目を追加")
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val libraryFields = fields.filter { it.name.isNotBlank() }.map { draft ->
                                LibraryField(
                                    id = draft.id,
                                    libraryId = libraryId.orEmpty(),
                                    name = draft.name,
                                    type = draft.type,
                                    position = 0,
                                    isRequired = draft.isRequired,
                                    options = draft.optionsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                )
                            }
                            if (libraryId == null) {
                                container.libraryRepository.createLibrary(name, description, "folder", libraryFields)
                            } else {
                                container.libraryRepository.renameLibrary(libraryId, name, description, "folder")
                                container.libraryRepository.replaceFields(libraryId, libraryFields)
                            }
                            onDone()
                        }
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("保存")
                }

                if (libraryId != null) {
                    TextButton(onClick = {
                        scope.launch {
                            container.libraryRepository.deleteLibrary(libraryId)
                            onDone()
                        }
                    }) {
                        Text("ライブラリを削除")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldDraftRow(field: FieldDraft, onChange: (FieldDraft) -> Unit, onRemove: () -> Unit) {
    var typeMenuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedTextField(
                    value = field.name,
                    onValueChange = { onChange(field.copy(name = it)) },
                    label = { Text("項目名") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "項目を削除")
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = field.type.label(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("種類") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // A transparent layer on top of the text field, rather than a clickable
                // modifier on the field itself: OutlinedTextField's own touch handling
                // (for cursor placement) can otherwise swallow the click before it opens
                // the menu, even when readOnly.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { typeMenuExpanded = true },
                )
                DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                    FieldType.entries.forEach { type ->
                        DropdownMenuItem(text = { Text(type.label()) }, onClick = {
                            onChange(field.copy(type = type))
                            typeMenuExpanded = false
                        })
                    }
                }
            }

            if (field.type == FieldType.CHOICE) {
                OutlinedTextField(
                    value = field.optionsText,
                    onValueChange = { onChange(field.copy(optionsText = it)) },
                    label = { Text("選択肢（カンマ区切り）") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = field.isRequired, onCheckedChange = { onChange(field.copy(isRequired = it)) })
                Text("必須項目にする")
            }
        }
    }
}

private fun FieldType.label(): String = when (this) {
    FieldType.TEXT -> "テキスト"
    FieldType.LONG_TEXT -> "長文テキスト"
    FieldType.NUMBER -> "数値"
    FieldType.DATE -> "日付"
    FieldType.BOOLEAN -> "チェックボックス"
    FieldType.CHOICE -> "選択肢"
    FieldType.LINK -> "リンク"
    FieldType.PHOTO -> "写真"
}
