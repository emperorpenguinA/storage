package com.mementostorage.app.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android

actual fun createHttpClient(): HttpClient = HttpClient(Android) {
    configureForDrive()
}
