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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mementostorage.app.di.AppContainer
import com.mementostorage.app.domain.model.Library

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryListScreen(
    container: AppContainer,
    onOpenLibrary: (String) -> Unit,
    onCreateLibrary: () -> Unit,
    onEditLibrary: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val libraries by container.libraryRepository.observeLibraries().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("マイライブラリ") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateLibrary) {
                Icon(Icons.Default.Add, contentDescription = "新しいライブラリ")
            }
        },
    ) { padding ->
        if (libraries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("まだライブラリがありません。右下の + から作成しましょう。")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(libraries, key = { it.id }) { library ->
                    LibraryRow(library = library, onOpen = { onOpenLibrary(library.id) }, onEdit = { onEditLibrary(library.id) })
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(library: Library, onOpen: () -> Unit, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        ListItem(
            headlineContent = { Text(library.name) },
            supportingContent = {
                Text(if (library.description.isBlank()) "${library.fields.size} 項目" else library.description)
            },
            trailingContent = {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Settings, contentDescription = "スキーマを編集")
                }
            },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        )
    }
}
