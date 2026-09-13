package com.mementostorage.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.mementostorage.app.ui.App
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // ComposeViewport appends its own canvas to <body> but never touches existing siblings,
    // so index.html's static "読み込み中…" placeholder would otherwise sit there forever
    // even after the app has actually finished loading and rendered underneath it.
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) {
        App()
    }
}
