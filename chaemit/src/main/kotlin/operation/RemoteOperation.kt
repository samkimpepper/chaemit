package operation

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.io.IOException

fun addRemoteRepository(chaemitDir: String, repositoryName: String) {
    val serverUrl = "http://localhost:8080/repositories"

    val client = OkHttpClient()
    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

    val jsonBody = """{"name": "$repositoryName"}"""
    val body = RequestBody.create(mediaType, jsonBody)

    val request = Request.Builder()
        .url(serverUrl)
        .post(body)
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("Failed to request $response")
        }
        val repoFile = File(chaemitDir, "REPO")
        val id = response.body?.string()
        id?.let { repoFile.writeText(it) }
        println("Remote repository $repositoryName successfully added as $id")
    }
}