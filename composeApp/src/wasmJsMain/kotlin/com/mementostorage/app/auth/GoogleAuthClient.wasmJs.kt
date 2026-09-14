package com.mementostorage.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.mementostorage.app.drive.createHttpClient
import com.mementostorage.app.util.nowEpochMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parseQueryString
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
private const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"

private const val KEY_ACCESS_TOKEN = "memento_storage.google.access_token"
private const val KEY_REFRESH_TOKEN = "memento_storage.google.refresh_token"
private const val KEY_EXPIRES_AT = "memento_storage.google.expires_at"
private const val KEY_EMAIL = "memento_storage.google.email"
private const val KEY_PKCE_VERIFIER = "memento_storage.google.pkce_verifier"

/**
 * Google's actual token/userinfo responses carry several fields (`scope`, `token_type`,
 * `id_token`, ...) beyond what [TokenResponse]/[UserInfoResponse] declare. Decoding with the
 * default strict [Json] rejects any response containing an undeclared key, which made every
 * sign-in silently fail right after the user granted consent — this lenient instance is what
 * [exchangeCodeForToken], [refreshAccessToken] and [fetchAccountEmail] must decode with instead.
 */
private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Signs in via Google's browser OAuth 2.0 "Authorization Code with PKCE" flow using a full
 * page redirect: no Google JS SDK is loaded, so this only relies on `window.location` and a
 * plain POST to Google's token endpoint (both plain, well-typed APIs from kotlinx-browser /
 * Ktor). PKCE's `code_challenge_method` is left as `plain` (challenge == verifier) rather
 * than `S256` to avoid pulling in WebCrypto/SubtleCrypto interop for this scaffold; Google
 * accepts `plain` for public clients, so this is a deliberate simplicity-over-hardening
 * tradeoff worth revisiting for production use.
 */
class WasmJsGoogleAuthClient(private val httpClient: HttpClient) : GoogleAuthClient {
    private val _authState = MutableStateFlow(loadPersistedState())
    override val authState: StateFlow<GoogleAuthState> = _authState

    // Computed once, synchronously, at construction — before completePendingSignInIfAny()
    // (which runs asynchronously from a LaunchedEffect) has a chance to consume the verifier.
    override val resumedFromSignInRedirect: Boolean =
        window.location.search.contains("code=") && localStorage.getItem(KEY_PKCE_VERIFIER) != null

    suspend fun completePendingSignInIfAny() {
        val search = window.location.search
        if (search.isEmpty() || !search.contains("code=")) return
        val code = parseQueryString(search.removePrefix("?"))["code"] ?: return
        val verifier = localStorage.getItem(KEY_PKCE_VERIFIER) ?: return
        localStorage.removeItem(KEY_PKCE_VERIFIER)

        runCatching { exchangeCodeForToken(code, verifier) }
            .onSuccess { persistTokenResponse(it) }
            .onFailure { error ->
                _authState.value = _authState.value.copy(
                    authError = "ログインに失敗しました: ${error.message}",
                )
            }

        // Drops the one-time-use `?code=...` (and friends) from the address bar so a manual
        // reload of this same URL doesn't look like a stuck/repeating sign-in attempt.
        window.history.replaceState(null, "", redirectUri())
    }

    override suspend fun signIn(): Result<Unit> = runCatching {
        _authState.value = _authState.value.copy(authError = null)
        val verifier = randomVerifier()
        localStorage.setItem(KEY_PKCE_VERIFIER, verifier)
        val redirectUri = redirectUri()
        val url = buildString {
            append(AUTH_ENDPOINT)
            append("?client_id=").append(GoogleOAuthConfig.webClientId.encodeURLParameter())
            append("&redirect_uri=").append(redirectUri.encodeURLParameter())
            append("&response_type=code")
            append("&scope=").append(DRIVE_FILE_SCOPE.encodeURLParameter())
            append("&access_type=offline")
            append("&prompt=consent")
            append("&code_challenge=").append(verifier.encodeURLParameter())
            append("&code_challenge_method=plain")
        }
        window.location.href = url
        // The page navigates away here; completePendingSignInIfAny() picks the flow back up
        // on reload.
    }

    override suspend fun signOut() {
        localStorage.removeItem(KEY_ACCESS_TOKEN)
        localStorage.removeItem(KEY_REFRESH_TOKEN)
        localStorage.removeItem(KEY_EXPIRES_AT)
        localStorage.removeItem(KEY_EMAIL)
        _authState.value = GoogleAuthState()
    }

    override suspend fun getAccessToken(): String? {
        val expiresAt = localStorage.getItem(KEY_EXPIRES_AT)?.toLongOrNull() ?: return null
        val currentToken = localStorage.getItem(KEY_ACCESS_TOKEN) ?: return null
        val now = nowEpochMillis()
        if (now < expiresAt - 60_000) return currentToken

        val refreshToken = localStorage.getItem(KEY_REFRESH_TOKEN) ?: return currentToken
        return runCatching { refreshAccessToken(refreshToken) }
            .onSuccess { persistTokenResponse(it) }
            .map { it.accessToken }
            .getOrElse { currentToken }
    }

    private suspend fun exchangeCodeForToken(code: String, verifier: String): TokenResponse {
        val response = httpClient.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("client_id", GoogleOAuthConfig.webClientId)
                // Required by Google for "Web application"-type clients even with PKCE — see
                // the tradeoff note on GoogleOAuthConfig.webClientSecret.
                append("client_secret", GoogleOAuthConfig.webClientSecret)
                append("grant_type", "authorization_code")
                append("code", code)
                append("code_verifier", verifier)
                append("redirect_uri", redirectUri())
            },
        )
        return response.bodyAsText().toTokenResponseOrThrow(response.status.isSuccess())
    }

    private suspend fun refreshAccessToken(refreshToken: String): TokenResponse {
        val response = httpClient.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("client_id", GoogleOAuthConfig.webClientId)
                append("client_secret", GoogleOAuthConfig.webClientSecret)
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            },
        )
        return response.bodyAsText().toTokenResponseOrThrow(response.status.isSuccess())
    }

    /**
     * Decoding straight into [TokenResponse] on a Google error response (e.g. `invalid_client`,
     * `invalid_grant`, `redirect_uri_mismatch`) just throws "access_token is required", which
     * hides the actual reason Google rejected the request — surface the real status/body instead
     * so it reaches [GoogleAuthState.authError] and is visible on screen.
     */
    private fun String.toTokenResponseOrThrow(wasSuccess: Boolean): TokenResponse {
        check(wasSuccess) { "Google からエラー応答: $this" }
        return lenientJson.decodeFromString(TokenResponse.serializer(), this)
    }

    private suspend fun persistTokenResponse(tokenResponse: TokenResponse) {
        val now = nowEpochMillis()
        localStorage.setItem(KEY_ACCESS_TOKEN, tokenResponse.accessToken)
        localStorage.setItem(KEY_EXPIRES_AT, (now + tokenResponse.expiresIn * 1000).toString())
        tokenResponse.refreshToken?.let { localStorage.setItem(KEY_REFRESH_TOKEN, it) }

        val email = runCatching { fetchAccountEmail(tokenResponse.accessToken) }.getOrNull()
        if (email != null) localStorage.setItem(KEY_EMAIL, email)
        _authState.value = GoogleAuthState(isSignedIn = true, accountEmail = email ?: localStorage.getItem(KEY_EMAIL))
    }

    private suspend fun fetchAccountEmail(accessToken: String): String {
        val response = httpClient.get(USERINFO_ENDPOINT) {
            header("Authorization", "Bearer $accessToken")
        }
        return lenientJson.decodeFromString(UserInfoResponse.serializer(), response.bodyAsText()).email
    }

    private fun redirectUri(): String = window.location.origin + window.location.pathname

    private fun loadPersistedState(): GoogleAuthState {
        val token = localStorage.getItem(KEY_ACCESS_TOKEN)
        val email = localStorage.getItem(KEY_EMAIL)
        return GoogleAuthState(isSignedIn = token != null, accountEmail = email)
    }
}

private fun randomVerifier(): String {
    val chars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..64).map { chars.random() }.joinToString("")
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
)

@Serializable
private data class UserInfoResponse(val email: String = "")

@Composable
actual fun rememberGoogleAuthClient(): GoogleAuthClient {
    val client = remember { WasmJsGoogleAuthClient(createHttpClient()) }
    LaunchedEffect(Unit) { client.completePendingSignInIfAny() }
    return client
}
