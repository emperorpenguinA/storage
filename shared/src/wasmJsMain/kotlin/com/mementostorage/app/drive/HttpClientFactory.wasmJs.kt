package com.mementostorage.app.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

// The `Js` engine (Fetch-API based) only ships a wasmJs variant from Ktor 3.x onward — 2.3.x's
// ktor-client-js is js-target-only, which is why `ktor` in gradle/libs.versions.toml is
// pinned to a 3.x release.
actual fun createHttpClient(): HttpClient = HttpClient(Js) {
    configureForDrive()
}
