package com.mementostorage.app.auth

import androidx.compose.runtime.Composable
import com.mementostorage.app.drive.DriveAuthTokenProvider
import kotlinx.coroutines.flow.StateFlow

data class GoogleAuthState(
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
    val authError: String? = null,
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

    /**
     * True right after this client is constructed on a page load that resumed a Google
     * sign-in redirect (web only — Android's sign-in never navigates away from the app, so it
     * has no page reload to recover from). Callers can check this once at startup to send the
     * user back to the screen they were on (typically Settings) instead of the app's normal
     * starting screen, which a full-page redirect would otherwise silently reset to.
     */
    val resumedFromSignInRedirect: Boolean

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
