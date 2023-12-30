package operation

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun push(chaemitDir: String) {
    val headFile = File("$chaemitDir/HEAD")
    val currentBranch = headFile.readText().trim().split(":").last().trim()
    val branchFile = File("$chaemitDir/$currentBranch")
    val commitHash = branchFile.readText().trim()
    val commitFile = File("$chaemitDir/objects/$commitHash")

    val changes = readCommitChanges(chaemitDir)

    val filesData = changes
        .map { (_, fileHash, fileType) ->
            var file: File? = null
            if (fileType == "tree") {
                file = File("$chaemitDir/objects/$fileHash")
            } else {
                val fileDirName = fileHash.substring(0, 2)
                val fileFileName = fileHash.substring(2)
                file = File("$chaemitDir/objects/$fileDirName/$fileFileName")
            }
            file
        }

    // 압축
    val zipFile = File("$chaemitDir/commit.zip")
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
        filesData.forEach { file ->
            val entryName = if (file.parentFile.name.length == 2) {
                "${file.parentFile.name}/${file.name}"
            } else {
                file.name
            }

            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            file.inputStream().use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }
        val commitEntry = ZipEntry(commitFile.name)
        zos.putNextEntry(commitEntry)
        commitFile.inputStream().use { input ->
            input.copyTo(zos)
        }
        zos.closeEntry()

        val branchesDir = File("$chaemitDir/refs/heads/")
        branchesDir.walk().filter { it.isFile }.forEach { branchFile ->
            val entry = ZipEntry(branchFile.relativeTo(File(chaemitDir)).path)
            zos.putNextEntry(entry)
            branchFile.inputStream().use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    // 요청
    val repositoryId = File(chaemitDir, "REPO").readText()
    val branchName = currentBranch.substringAfterLast("/")
    val serverUrl = "http://localhost:8080/repositories/$repositoryId/branches/$branchName"

    val client = OkHttpClient()

    val requestBodyBuilder = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("commit", "commit.txt", RequestBody.create("text/plain".toMediaTypeOrNull(), commitFile))
        .addFormDataPart("objects", "objects.zip", RequestBody.create("application/zip".toMediaTypeOrNull(), zipFile))

    val request = Request.Builder()
        .url(serverUrl)
        .post(requestBodyBuilder.build())
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            println("Failed to push")
        } else {
            println("Push successful")
        }
    }

}

private fun readCommitChanges(chaemitDir: String): List<Triple<String, String, String>> {
    val headFile = File("$chaemitDir/HEAD")
    val currentBranch = headFile.readText().trim().split(":").last().trim()
    val branchFile = File("$chaemitDir/$currentBranch")
    val commitHash = branchFile.readText().trim()
    val commitFile = File("$chaemitDir/objects/$commitHash")
    val commitContent = commitFile.readText()
    val changesSection = commitContent.split("Changes:\n").getOrElse(1) { "" }
    return changesSection.lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val parts = line.split(" ")
            val filePath = parts[0]
            val fileHash = parts[1]
            val fileType = parts[2]
            Triple(filePath, fileHash, fileType)
        }
}