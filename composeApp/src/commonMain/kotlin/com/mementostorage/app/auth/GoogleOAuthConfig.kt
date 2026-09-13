package com.mementostorage.app.auth

/**
 * Fill in [webClientId] and [webClientSecret] with the values from the "Web application"
 * OAuth 2.0 Client ID created in Google Cloud Console for this project (APIs & Services >
 * Credentials). Only the wasmJs target uses these — Android instead identifies itself to
 * Google via its own OAuth client entry (package name + signing SHA-1), configured
 * separately, with no string to paste into code. See README "Google Drive setup" for the
 * full walkthrough.
 *
 * Google's token endpoint requires [webClientSecret] for "Web application"-type clients when
 * exchanging an authorization code, even though this flow also uses PKCE — Google does not
 * treat browser-based web clients as "public" clients the way it does for Android/iOS/Desktop.
 * That means this secret ships inside the compiled wasmJs bundle, where anyone could extract
 * it. It is not a real secret in that sense; it only works as access control together with the
 * `drive.file` scope (an attacker can't reach a user's other files) and the redirect URI
 * allowlist configured in Cloud Console. Treat this app as personal/private-use, not as
 * something to distribute publicly while relying on this value staying secret.
 */
object GoogleOAuthConfig {
    const val webClientId: String = "463986432173-41oqbp21q8uudbij4p0ug1gb283veh9a.apps.googleusercontent.com"
    const val webClientSecret: String = "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"
}
