package com.mementostorage.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mementostorage.app.auth.rememberGoogleAuthClient
import com.mementostorage.app.di.rememberAppContainer
import com.mementostorage.app.ui.screens.EntryEditorScreen
import com.mementostorage.app.ui.screens.EntryListScreen
import com.mementostorage.app.ui.screens.LibraryEditorScreen
import com.mementostorage.app.ui.screens.LibraryListScreen
import com.mementostorage.app.ui.screens.SettingsScreen

/**
 * Root composable, identical on Android and web. Navigation is a plain in-memory back stack
 * (no navigation-compose dependency) since a handful of screens don't need deep-linking or
 * saved-instance-state restoration to be useful.
 */
@Composable
fun App() {
    MaterialTheme(typography = rememberJapaneseTypography()) {
        Surface(modifier = Modifier) {
            val authClient = rememberGoogleAuthClient()
            val container = rememberAppContainer(authClient)

            // A web sign-in redirect reloads the whole page, wiping this in-memory back stack —
            // resumedFromSignInRedirect lets us land back on Settings instead of silently
            // resetting to the app's normal starting screen right after the user logs in.
            var backStack by remember {
                mutableStateOf(
                    if (authClient.resumedFromSignInRedirect) {
                        listOf(Screen.LibraryList, Screen.Settings)
                    } else {
                        listOf(Screen.LibraryList)
                    },
                )
            }
            fun push(screen: Screen) {
                backStack = backStack + screen
            }
            fun pop() {
                if (backStack.size > 1) backStack = backStack.dropLast(1)
            }

            when (val screen = backStack.last()) {
                is Screen.LibraryList -> LibraryListScreen(
                    container = container,
                    onOpenLibrary = { push(Screen.EntryList(it)) },
                    onCreateLibrary = { push(Screen.LibraryEditor(libraryId = null)) },
                    onEditLibrary = { push(Screen.LibraryEditor(libraryId = it)) },
                    onOpenSettings = { push(Screen.Settings) },
                )

                is Screen.LibraryEditor -> LibraryEditorScreen(
                    container = container,
                    libraryId = screen.libraryId,
                    onDone = { pop() },
                )

                is Screen.EntryList -> EntryListScreen(
                    container = container,
                    libraryId = screen.libraryId,
                    onBack = { pop() },
                    onEditSchema = { push(Screen.LibraryEditor(libraryId = screen.libraryId)) },
                    onOpenEntry = { entryId -> push(Screen.EntryEditor(libraryId = screen.libraryId, entryId = entryId)) },
                    onCreateEntry = { push(Screen.EntryEditor(libraryId = screen.libraryId, entryId = null)) },
                )

                is Screen.EntryEditor -> EntryEditorScreen(
                    container = container,
                    libraryId = screen.libraryId,
                    entryId = screen.entryId,
                    onDone = { pop() },
                )

                is Screen.Settings -> SettingsScreen(
                    container = container,
                    authClient = authClient,
                    onBack = { pop() },
                )
            }
        }
    }
}
