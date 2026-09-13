package com.mementostorage.app.auth

/**
 * Fill in [webClientId] with the "Web application" OAuth 2.0 Client ID created in Google
 * Cloud Console for this project (APIs & Services > Credentials). Only the wasmJs target
 * uses this — Android instead identifies itself to Google via its own OAuth client entry
 * (package name + signing SHA-1), configured separately, with no string to paste into code.
 * See README "Google Drive setup" for the full walkthrough.
 */
object GoogleOAuthConfig {
    const val webClientId: String = "REPLACE_WITH_YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
}
