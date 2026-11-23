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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mementostorage.app.di.AppContainer
import com.mementostorage.app.domain.model.Library
import com.mementostorage.app.ui.components.SortCriterion
import com.mementostorage.app.ui.components.SortDirection
import com.mementostorage.app.ui.components.SortMenuButton
import com.mementostorage.app.ui.components.SortSpec
import com.mementostorage.app.ui.components.sortedBySpec

private val LIBRARY_SORT_CRITERIA = listOf(
    SortCriterion(id = "name", label = "名前順"),
    SortCriterion(id = "updated", label = "更新日時順", defaultDirection = SortDirection.DESCENDING),
    SortCriterion(id = "created", label = "作成日時順", defaultDirection = SortDirection.DESCENDING),
)

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
    var sortSpec by remember { mutableStateOf(SortSpec("name", SortDirection.ASCENDING)) }
    val sortedLibraries = remember(libraries, sortSpec) {
        val comparator = when (sortSpec.criterionId) {
            "updated" -> compareBy<Library> { it.updatedAt }
            "created" -> compareBy<Library> { it.createdAt }
            else -> compareBy { it.name }
        }
        libraries.sortedBySpec(comparator, sortSpec)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("マイライブラリ") },
                actions = {
                    SortMenuButton(criteria = LIBRARY_SORT_CRITERIA, spec = sortSpec, onSpecChange = { sortSpec = it })
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
        if (sortedLibraries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("まだライブラリがありません。右下の + から作成しましょう。")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(sortedLibraries, key = { it.id }) { library ->
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
