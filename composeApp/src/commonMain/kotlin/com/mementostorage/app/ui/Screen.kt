package com.mementostorage.app.ui

sealed interface Screen {
    data object LibraryList : Screen
    data class LibraryEditor(val libraryId: String?) : Screen
    data class EntryList(val libraryId: String) : Screen
    data class EntryEditor(val libraryId: String, val entryId: String?) : Screen
    data object Settings : Screen
}
