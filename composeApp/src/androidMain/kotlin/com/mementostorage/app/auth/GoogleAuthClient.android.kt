package com.mementostorage.app.auth

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/**
 * Uses Google's Authorization API (play-services-auth) to obtain a `drive.file`-scoped
 * access token. This purposely does not use the older GoogleSignInClient (deprecated) nor
 * full Credential Manager sign-in (which authenticates identity, not scoped API access) —
 * AuthorizationClient is Google's current, more direct path to an OAuth token for a specific
 * scope like Drive. It still requires an OAuth client ID configured in Google Cloud Console
 * for this app's package name + SHA-1 (see README "Google Drive setup").
 */
class AndroidGoogleAuthClient(context: Context) : GoogleAuthClient {
    private val authorizationClient = Identity.getAuthorizationClient(context)
    private val _authState = MutableStateFlow(GoogleAuthState())
    override val authState: StateFlow<GoogleAuthState> = _authState
    override val resumedFromSignInRedirect: Boolean = false

    private var cachedToken: String? = null
    private var pendingContinuation: CancellableContinuation<AuthorizationResult>? = null

    /** Wired up by [rememberGoogleAuthClient] once the launcher is registered. */
    var launchResolution: ((IntentSenderRequest) -> Unit)? = null

    override suspend fun signIn(): Result<Unit> = runCatching {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
        val result = requestAuthorization(request)
        cachedToken = result.accessToken
        _authState.value = GoogleAuthState(isSignedIn = true, accountEmail = _authState.value.accountEmail)
    }

    override suspend fun signOut() {
        cachedToken = null
        _authState.value = GoogleAuthState()
        // Revoking the grant itself is left to the user via their Google Account settings;
        // AuthorizationClient has no direct revoke call, only "forget this token locally".
    }

    override suspend fun getAccessToken(): String? {
        if (!_authState.value.isSignedIn) return null
        return runCatching {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            requestAuthorization(request).accessToken.also { cachedToken = it }
        }.getOrNull() ?: cachedToken
    }

    private suspend fun requestAuthorization(request: AuthorizationRequest): AuthorizationResult =
        suspendCancellableCoroutine { continuation ->
            authorizationClient.authorize(request)
                .addOnSuccessListener { authResult ->
                    val pendingIntent = authResult.pendingIntent
                    if (authResult.hasResolution() && pendingIntent != null) {
                        pendingContinuation = continuation
                        val resolve = launchResolution
                        if (resolve == null) {
                            continuation.resumeWithException(IllegalStateException("No Activity available to complete Google sign-in"))
                        } else {
                            resolve(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                        }
                    } else {
                        continuation.resume(authResult)
                    }
                }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }

    fun onAuthorizationActivityResult(result: ActivityResult) {
        val continuation = pendingContinuation ?: return
        pendingContinuation = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching { authorizationClient.getAuthorizationResultFromIntent(result.data!!) }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        } else {
            continuation.resumeWithException(IllegalStateException("Google sign-in was cancelled"))
        }
    }
}

@Composable
actual fun rememberGoogleAuthClient(): GoogleAuthClient {
    val context = LocalContext.current
    val client = remember { AndroidGoogleAuthClient(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        client.onAuthorizationActivityResult(result)
    }
    client.launchResolution = { launcher.launch(it) }
    return client
}
