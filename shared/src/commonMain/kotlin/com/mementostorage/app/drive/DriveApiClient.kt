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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

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
        val list = Json.decodeFromString(DriveFileListDto.serializer(), response.body())
        return list.files.firstOrNull()?.toDomain()
    }

    /** Finds (or creates, on first run) the single folder MementoStorage keeps all of its data in. */
    suspend fun ensureAppFolder(folderName: String = "MementoStorageApp"): String {
        val token = authToken()
        val query = "mimeType = '$FOLDER_MIME_TYPE' and name = '${folderName.escapeForDriveQuery()}' and 'root' in parents and trashed = false"
        val response: HttpResponse = httpClient.get(DRIVE_FILES_URL) {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
            parameter("fields", "files(id,name,mimeType)")
            parameter("spaces", "drive")
        }
        val existing = Json.decodeFromString(DriveFileListDto.serializer(), response.body()).files.firstOrNull()
        if (existing != null) return existing.id

        val createResponse: HttpResponse = httpClient.post(DRIVE_FILES_URL) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateFolderRequest.serializer(), CreateFolderRequest(name = folderName)))
        }
        return Json.decodeFromString(DriveFileDto.serializer(), createResponse.body()).id
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
        return Json.decodeFromString(DriveFileDto.serializer(), response.body<String>()).toDomain()
    }

    suspend fun downloadText(fileId: String): String = downloadBytes(fileId).decodeToString()

    suspend fun downloadBytes(fileId: String): ByteArray {
        val token = authToken()
        val response: HttpResponse = httpClient.get("$DRIVE_FILES_URL/$fileId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("alt", "media")
        }
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
