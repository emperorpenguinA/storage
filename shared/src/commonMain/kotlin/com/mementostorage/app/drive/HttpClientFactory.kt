package com.mementostorage.app.drive

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Builds the [HttpClient] used for all Google Drive REST calls, using each platform's engine. */
expect fun createHttpClient(): HttpClient

private val driveJson = Json { ignoreUnknownKeys = true }

/** Shared plugin setup every platform's [createHttpClient] should apply. */
fun HttpClientConfig<*>.configureForDrive() {
    install(ContentNegotiation) { json(driveJson) }
    install(Logging) { level = LogLevel.INFO }
}
