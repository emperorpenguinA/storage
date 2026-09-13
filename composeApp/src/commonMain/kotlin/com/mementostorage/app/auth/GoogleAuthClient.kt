package com.mementostorage.app.auth

import androidx.compose.runtime.Composable
import com.mementostorage.app.drive.DriveAuthTokenProvider
import kotlinx.coroutines.flow.StateFlow

data class GoogleAuthState(
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
)

/**
 * Owns the Google sign-in UX for the Drive backup feature and, once signed in, hands out
 * access tokens to [com.mementostorage.app.drive.SyncService] via [DriveAuthTokenProvider].
 * The `shared` module never touches Compose or platform Activities/browsers directly — this
 * interface is the seam between "getting a token" (shared, simple) and "getting the user's
 * consent" (very platform-specific, lives here).
 */
interface GoogleAuthClient : DriveAuthTokenProvider {
    val authState: StateFlow<GoogleAuthState>
    suspend fun signIn(): Result<Unit>
    suspend fun signOut()
}

/**
 * Provides the platform [GoogleAuthClient]. It is `@Composable` because the Android
 * implementation must register an ActivityResultLauncher, which can only be created while
 * composing.
 */
@Composable
expect fun rememberGoogleAuthClient(): GoogleAuthClient
