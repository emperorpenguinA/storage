import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "shared"
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.android)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(libs.ktor.client.js)
            }
        }
    }
}

android {
    namespace = "com.mementostorage.app.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// The .sq schema lives under src/androidMain/sqldelight: SQLDelight backs local storage on
// Android only. The wasmJs target uses a much simpler JSON-in-localStorage store instead
// (see data/local/WasmJsLocalStore.kt) because SQLDelight's browser drivers (sql.js / a
// worker-hosted SQLite-Wasm) are still a fast-moving, version-sensitive part of the
// ecosystem; a flat JSON document is more than enough data for a personal database app and
// keeps the web target on plain, dependable browser APIs.
sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.mementostorage.app.db")
            // Default dialect is SQLite 3.18, which predates the `ON CONFLICT ... DO UPDATE`
            // (upsert) syntax the .sq files use for restoring a Drive backup. 3.38 comfortably
            // covers what any Android version in minSdk's range ships.
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:${libs.versions.sqldelight.get()}")
        }
    }
}
