package operation

import okhttp3.OkHttpClient
import okhttp3.Request
import util.FileUtils
import java.io.File

fun pull(chaemitDir: String, branchName: String) {
    val repositoryId = File(chaemitDir, "REPO").readText()
    if (repositoryId == "-1") {
        println("Error: No valid repository ID found. Please establish a connection with a remote repository first.")
        return
    }

    val serverUrl = "http://localhost:8080/repositories/$repositoryId/branches/$branchName"

    val client = OkHttpClient()

    val request = Request.Builder()
        .url(serverUrl)
        .get()
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            println("Failed to pull")
        } else {
            println("Pull successful")

            val fileContent = response.body?.bytes() ?: return
            val zipFilePath = "$chaemitDir/objects/$branchName.zip"
            File(zipFilePath).writeBytes(fileContent)

            FileUtils.unzip(zipFilePath, "$chaemitDir/objects", chaemitDir)
        }
    }

    val branch = File("$chaemitDir/refs/heads/$branchName")
    val commitHash = branch.readText().trim()
    val commitFile = File("$chaemitDir/objects/$commitHash")
    val commitContent = commitFile.readText()
    val changesSection = commitContent.split("Changes:\n").getOrElse(1) { "" }

    val firstLineOfChanges = changesSection.lines().firstOrNull() ?: ""
    val parts = firstLineOfChanges.split(" ")
    val path = parts.getOrNull(0)?: ""
    val checksum = parts.getOrNull(1) ?: ""
    FileUtils.restoreWorkingDirectory(path, checksum, chaemitDir)

}