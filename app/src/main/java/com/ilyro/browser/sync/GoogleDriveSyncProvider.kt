package com.ilyro.browser.sync

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Stores one versioned ILYRO settings snapshot in Google Drive's hidden appDataFolder.
 * Only the narrow drive.appdata scope is required.
 */
class GoogleDriveSyncProvider(
    private val accessToken: String
) : SyncProvider {
    override val id: String = "google_drive_appdata"

    override suspend fun isAvailable(): Boolean = accessToken.isNotBlank()

    override suspend fun downloadLatest(): SyncResult<SyncSnapshot?> = withContext(Dispatchers.IO) {
        try {
            val files = listFiles()
            val latest = files.firstOrNull() ?: return@withContext SyncResult.Success(null)
            val response = request(
                method = "GET",
                url = "$DRIVE_API/files/${latest.id}?alt=media"
            )
            if (!response.isSuccessful) {
                return@withContext response.asFailure()
            }
            SyncResult.Success(SyncSnapshotCodec.decode(response.body))
        } catch (error: Exception) {
            SyncResult.Failure(error.message)
        }
    }

    override suspend fun upload(snapshot: SyncSnapshot): SyncResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val previousFiles = listFiles()
            val payload = SyncSnapshotCodec.encode(snapshot)
            val boundary = "ilyro-${UUID.randomUUID()}"
            val metadata = JSONObject()
                .put("name", FILE_NAME)
                .put("parents", org.json.JSONArray().put("appDataFolder"))
                .put("mimeType", JSON_MIME)
                .toString()

            val body = buildMultipartBody(boundary, metadata, payload)
            val response = request(
                method = "POST",
                url = "$DRIVE_UPLOAD_API/files?uploadType=multipart&fields=id",
                body = body,
                contentType = "multipart/related; boundary=$boundary"
            )
            if (!response.isSuccessful) {
                return@withContext response.asFailure()
            }

            // The new snapshot is already safely stored. Old duplicates can now be removed.
            previousFiles.forEach { oldFile ->
                request(
                    method = "DELETE",
                    url = "$DRIVE_API/files/${oldFile.id}"
                )
            }
            SyncResult.Success(Unit)
        } catch (error: Exception) {
            SyncResult.Failure(error.message)
        }
    }

    override suspend fun deleteRemoteData(): SyncResult<Unit> = withContext(Dispatchers.IO) {
        try {
            var firstFailure: SyncResult.Failure? = null
            listFiles().forEach { file ->
                val response = request(
                    method = "DELETE",
                    url = "$DRIVE_API/files/${file.id}"
                )
                if (!response.isSuccessful && firstFailure == null) {
                    firstFailure = response.asFailure()
                }
            }
            firstFailure ?: SyncResult.Success(Unit)
        } catch (error: Exception) {
            SyncResult.Failure(error.message)
        }
    }

    private fun listFiles(): List<RemoteFile> {
        val query = "name='$FILE_NAME' and trashed=false"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val encodedFields = URLEncoder.encode("files(id,modifiedTime)", StandardCharsets.UTF_8.name())
        val response = requestBlocking(
            method = "GET",
            url = "$DRIVE_API/files?spaces=appDataFolder&q=$encodedQuery&orderBy=modifiedTime%20desc&pageSize=100&fields=$encodedFields"
        )
        if (!response.isSuccessful) {
            throw DriveApiException(response.code, response.body)
        }

        val files = JSONObject(response.body).optJSONArray("files") ?: return emptyList()
        return buildList {
            for (index in 0 until files.length()) {
                val item = files.getJSONObject(index)
                val fileId = item.optString("id")
                if (fileId.isNotBlank()) {
                    add(RemoteFile(fileId))
                }
            }
        }
    }

    private fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null
    ): HttpResponse = requestBlocking(method, url, body, contentType)

    private fun requestBlocking(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (contentType != null) {
                setRequestProperty("Content-Type", contentType)
            }
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }

        return try {
            if (body != null) {
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildMultipartBody(boundary: String, metadata: String, payload: String): ByteArray {
        val output = ByteArrayOutputStream()
        fun write(value: String) = output.write(value.toByteArray(StandardCharsets.UTF_8))

        write("--$boundary\r\n")
        write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        write(metadata)
        write("\r\n--$boundary\r\n")
        write("Content-Type: $JSON_MIME; charset=UTF-8\r\n\r\n")
        write(payload)
        write("\r\n--$boundary--\r\n")
        return output.toByteArray()
    }

    private data class RemoteFile(val id: String)

    private data class HttpResponse(val code: Int, val body: String) {
        val isSuccessful: Boolean get() = code in 200..299

        fun asFailure(): SyncResult.Failure = SyncResult.Failure(
            message = "Google Drive API error $code${body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}",
            recoverable = code == 401 || code == 403 || code == 429 || code >= 500
        )
    }

    private class DriveApiException(code: Int, body: String) : RuntimeException(
        "Google Drive API error $code${body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
    )

    private companion object {
        const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        const val DRIVE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_NAME = "ilyro_settings_v1.json"
        const val JSON_MIME = "application/json"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
