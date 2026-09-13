package com.mementostorage.app.drive

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

/**
 * Drive's actual responses carry fields (`kind`, `mimeType` on list results without `fields`
 * scoping applied server-side to every nested object, etc.) beyond what [DriveFileDto]/
 * [DriveFileListDto] declare. The default strict [Json] rejects any response containing an
 * undeclared key, which made every upload/list/create call here fail — see the identical bug
 * already fixed in GoogleAuthClient.wasmJs.kt.
 *
 * `encodeDefaults = true` matters for encoding [CreateFolderRequest]: its `mimeType` property
 * always holds its own default value, and Json's default `encodeDefaults = false` silently
 * omits any property left at its default — so the request body Drive received was just
 * `{"name":"..."}` with no `mimeType` at all. Drive then created a plain file (defaulting to
 * `application/octet-stream`) instead of a folder, which every later upload into it then
 * rejected with a `parentNotAFolder` error.
 */
private val driveJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Decoding an error response (401/403/404/...) straight into [DriveFileDto]/[DriveFileListDto]
 * only reported "field 'id' is required", hiding why Drive actually rejected the call — this
 * surfaces the real status/body so it reaches the caller (and, via SyncService's Result, the
 * Settings screen) instead.
 */
private suspend fun HttpResponse.textOrThrow(): String {
    val text = bodyAsText()
    check(status.isSuccess()) { "Google Drive がエラー応答を返しました ($status): $text" }
    return text
}

class DriveAuthException(message: String) : Exception(message)

data class DriveFile(val id: String, val name: String, val mimeType: String)

/**
 * Thin wrapper over the Google Drive REST API v3 (https://developers.google.com/drive/api/reference/rest/v3),
 * scoped to what MementoStorage needs: find/create one app folder, and upload/download/list/delete
 * files inside it. Every call needs the `drive.file` OAuth scope, which only grants access to
 * files this app itself created — Drive contents outside that are never touched.
 */
class DriveApiClient(
    private val httpClient: HttpClient,
    private val tokenProvider: DriveAuthTokenProvider,
) {
    // A list screen can show many PHOTO thumbnails at once, each independently calling
    // downloadBytes(); firing them all at the same instant risks Drive rate-limiting several
    // of them, so cap how many of these (large, comparatively slow) downloads run at once.
    private val downloadSemaphore = Semaphore(permits = 4)

    private suspend fun authToken(): String =
        tokenProvider.getAccessToken() ?: throw DriveAuthException("Not signed in to Google Drive")

    suspend fun findFileByName(parentId: String, fileName: String, mimeType: String? = null): DriveFile? {
        val token = authToken()
        val mimeFilter = if (mimeType != null) " and mimeType = '$mimeType'" else ""
        val query = "'$parentId' in parents and name = '${fileName.escapeForDriveQuery()}' and trashed = false$mimeFilter"
        val response: HttpResponse = httpClient.get(DRIVE_FILES_URL) {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
            parameter("fields", "files(id,name,mimeType)")
            parameter("spaces", "drive")
        }
        val list = driveJson.decodeFromString(DriveFileListDto.serializer(), response.textOrThrow())
        return list.files.firstOrNull()?.toDomain()
    }

    private suspend fun findAppFolderByName(name: String): DriveFile? {
        val token = authToken()
        val query = "mimeType = '$FOLDER_MIME_TYPE' and name = '${name.escapeForDriveQuery()}' and 'root' in parents and trashed = false"
        val response: HttpResponse = httpClient.get(DRIVE_FILES_URL) {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
            parameter("fields", "files(id,name,mimeType)")
            parameter("spaces", "drive")
        }
        return driveJson.decodeFromString(DriveFileListDto.serializer(), response.textOrThrow()).files.firstOrNull()?.toDomain()
    }

    /**
     * Finds (or creates, on first run) the single folder this app keeps all of its data in.
     *
     * [legacyFolderNames] lists names this folder was called under an earlier version of the
     * app (e.g. before a rename of the app itself). If [folderName] isn't found but one of
     * these is, that folder is renamed in place rather than leaving it behind and creating a
     * fresh, empty-looking [folderName] folder that silently orphans an existing backup.
     */
    suspend fun ensureAppFolder(folderName: String = "ストレージ", legacyFolderNames: List<String> = listOf("MementoStorageApp")): String {
        findAppFolderByName(folderName)?.let { return it.id }

        for (legacyName in legacyFolderNames) {
            val legacyFolder = findAppFolderByName(legacyName) ?: continue
            return renameFile(legacyFolder.id, folderName).id
        }

        val token = authToken()
        val createResponse: HttpResponse = httpClient.post(DRIVE_FILES_URL) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(driveJson.encodeToString(CreateFolderRequest.serializer(), CreateFolderRequest(name = folderName)))
        }
        return driveJson.decodeFromString(DriveFileDto.serializer(), createResponse.textOrThrow()).id
    }

    suspend fun renameFile(fileId: String, newName: String): DriveFile {
        val token = authToken()
        val response: HttpResponse = httpClient.patch("$DRIVE_FILES_URL/$fileId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"${newName.escapeForJson()}"}""")
        }
        return driveJson.decodeFromString(DriveFileDto.serializer(), response.textOrThrow()).toDomain()
    }

    suspend fun uploadText(parentId: String, fileName: String, mimeType: String, content: String, existingFileId: String? = null): DriveFile =
        uploadBytes(parentId, fileName, mimeType, content.encodeToByteArray(), existingFileId)

    suspend fun uploadBytes(
        parentId: String,
        fileName: String,
        mimeType: String,
        content: ByteArray,
        existingFileId: String? = null,
    ): DriveFile {
        val token = authToken()
        val boundary = "MementoStorageBoundary${randomBoundarySuffix()}"
        val metadata = if (existingFileId == null) {
            """{"name":"${fileName.escapeForJson()}","parents":["$parentId"]}"""
        } else {
            """{"name":"${fileName.escapeForJson()}"}"""
        }
        val body = buildMultipartRelatedBody(metadata, mimeType, content, boundary)
        val url = if (existingFileId == null) DRIVE_UPLOAD_URL else "$DRIVE_UPLOAD_URL/$existingFileId"

        val response: HttpResponse = if (existingFileId == null) {
            httpClient.post(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("uploadType", "multipart")
                contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                setBody(ByteArrayContent(body, ContentType.parse("multipart/related; boundary=$boundary")))
            }
        } else {
            httpClient.patch(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("uploadType", "multipart")
                contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                setBody(ByteArrayContent(body, ContentType.parse("multipart/related; boundary=$boundary")))
            }
        }
        return driveJson.decodeFromString(DriveFileDto.serializer(), response.textOrThrow()).toDomain()
    }

    suspend fun downloadText(fileId: String): String = downloadBytes(fileId).decodeToString()

    suspend fun downloadBytes(fileId: String): ByteArray {
        val token = authToken()
        val response: HttpResponse = downloadSemaphore.withPermit {
            httpClient.get("$DRIVE_FILES_URL/$fileId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("alt", "media")
            }
        }
        // Unlike the other calls in this class, this response was never run through
        // textOrThrow(): an error response (429 rate-limited, 401 expired token, ...) was
        // silently accepted as if its JSON error body were the file's actual bytes, which then
        // got cached to disk/IndexedDB as a permanently-corrupt "photo" that never decodes.
        check(response.status.isSuccess()) { "Google Drive がエラー応答を返しました (${response.status}): ${response.bodyAsText()}" }
        return response.body()
    }

    suspend fun deleteFile(fileId: String) {
        val token = authToken()
        httpClient.delete("$DRIVE_FILES_URL/$fileId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}

private fun buildMultipartRelatedBody(metadataJson: String, contentType: String, content: ByteArray, boundary: String): ByteArray {
    val header = buildString {
        append("--").append(boundary).append("\r\n")
        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        append(metadataJson).append("\r\n")
        append("--").append(boundary).append("\r\n")
        append("Content-Type: ").append(contentType).append("\r\n\r\n")
    }.encodeToByteArray()
    val footer = "\r\n--$boundary--".encodeToByteArray()
    return header + content + footer
}

private fun String.escapeForDriveQuery(): String = replace("\\", "\\\\").replace("'", "\\'")
private fun String.escapeForJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")

/** A short, non-cryptographic uniquifier for multipart boundaries; collisions are harmless here. */
private fun randomBoundarySuffix(): String {
    val chars = ('a'..'z') + ('0'..'9')
    return (1..8).map { chars.random() }.joinToString("")
}

@Serializable
private data class DriveFileDto(val id: String, val name: String = "", val mimeType: String = "") {
    fun toDomain() = DriveFile(id, name, mimeType)
}

@Serializable
private data class DriveFileListDto(val files: List<DriveFileDto> = emptyList())

@Serializable
private data class CreateFolderRequest(
    val name: String,
    val mimeType: String = FOLDER_MIME_TYPE,
)
