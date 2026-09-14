package com.mementostorage.app.drive

/**
 * Hands [DriveApiClient] a valid OAuth2 access token carrying the `drive.file` scope.
 * Implemented on each platform by whatever owns the actual sign-in UX (see
 * composeApp's GoogleAuthClient) so this module never depends on Compose or Android.
 */
interface DriveAuthTokenProvider {
    /** Returns null when the user has not connected a Google account. */
    suspend fun getAccessToken(): String?
}
