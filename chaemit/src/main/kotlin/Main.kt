
import com.github.kittinunf.fuel.core.FuelManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.*
import java.lang.StringBuilder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.*
import kotlin.IllegalStateException

fun createChecksum(filePath: String): String {
    val file = File(filePath)
    val digest = MessageDigest.getInstance("SHA-1")

    digest.update(filePath.toByteArray())

    file.inputStream().use { fis ->
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }

    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun createChecksumForInput(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(input.toByteArray())
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun saveBlobObject(filePath: String, chaemitDir: String, checksum: String) {
    val file = File(filePath)
    val fileContent = file.readBytes()

    val objectDir = File("$chaemitDir/objects/${checksum.substring(0, 2)}")
    if (!objectDir.exists()) {
        objectDir.mkdir()
    }

    val objectFile = File(objectDir, checksum.substring(2))
    if (!objectFile.exists()) {
        FileOutputStream(objectFile).use { fos ->
            DeflaterOutputStream(fos).use { dos ->
                dos.write(fileContent)
            }
        }
    }
}

private fun saveTreeObject(name: String, chaemitDir: String, checksum: String, entries: List<String>) {
    val treeObjectFile = File(chaemitDir, "objects/$checksum")
    treeObjectFile.writeText(entries.joinToString("\n"))
}




//private fun createChecksumForDirectory(directory: File, lastCommitContent: String): String {
//    val entries = directory.listFiles()
//        ?.filter { it.name != ".chaemit" && it.name != ".DS_Store" }
//        ?.sortedWith(compareBy { it.name }) // 파일 이름으로 정렬
//        ?.joinToString("\n") { subFile ->
//            if (subFile.absolutePath.contains(".chaemit") || subFile.absolutePath.contains(".DS_Store")) {
//                ""
//            } else {
//                val subFileType = if (subFile.isDirectory) "tree" else "blob"
//                val subFileChecksum = if (subFile.isDirectory) createChecksumForDirectory(subFile, lastCommitContent) else createChecksum(subFile.path)
//                val subFilePath = getRelativePath(subFile, System.getProperty("user.dir") + "/.chaemit")
//                var status = ""
//                lastCommitContent.lines()
//                    .map { line ->
//                        when {
//                            !(line.contains(subFileChecksum) || line.contains(subFilePath)) -> {
//                                status = "added"
//                            }
//                            line.contains(subFileChecksum) && !line.contains(subFilePath) -> {
//                                val parts = line.split(" ")
//                                val lastPath = parts[0]
//                                val lastChecksum = parts[1]
//                                val lastDir = lastPath.substringBeforeLast("/")
//                                val currentDir = subFile.path.substringBeforeLast("/")
//
//                                status = if (lastDir != currentDir) "moved" else "renamed"
//                            }
//                            line.contains(subFilePath) && !line.contains(subFileChecksum) -> {
//                                status = "modified"
//                            }
//                            line.contains(subFileChecksum) && line.contains(subFilePath) -> {
//                                return@map
//                            }
//                        }
//                    }
//                "$subFilePath $subFileChecksum $subFileType $status" // 전체 경로 대신 파일 이름 사용
//            }
//        } ?: ""
//
//    return createChecksumForInput(entries)
//}

fun initializeChaemitRepository() {
    val currentDir = System.getProperty("user.dir")
    val chaemitDir = File(currentDir, ".chaemit")
    val objectsDir = File(chaemitDir, "objects")
    val refsDir = File(chaemitDir, "refs")
    val headsDir = File(refsDir, "heads")
    val headFile = File(chaemitDir, "HEAD")
    val repoFile = File(chaemitDir, "REPO")
    val mainBranchFile = File(headsDir, "main")

    if (!chaemitDir.exists())
        chaemitDir.mkdir()

    if (!objectsDir.exists())
        objectsDir.mkdir()

    if (!refsDir.exists())
        refsDir.mkdir()

    if (!headsDir.exists())
        headsDir.mkdir()

    if (!headFile.exists())
        headFile.writeText("ref: refs/heads/main")

    if (!repoFile.exists())
        repoFile.writeText("-1")

    if(!mainBranchFile.exists())
        mainBranchFile.createNewFile()

    println("Initialized empty Chaemit repository in ${chaemitDir.path}")
}



fun addToStagingArea(target: String, chaemitDir: String) {
    val fileToAdd = if (target == ".") {
        File(System.getProperty("user.dir")).absoluteFile
    } else {
        File(target).absoluteFile
    }

    val headFile = File(chaemitDir, "HEAD")
    val currentBranch = headFile.readText().trim().split(": ").last()
    val branchFile = File(chaemitDir, currentBranch)
    val lastCommitHash = branchFile.readText().trim()
    val lastCommitFile = File(chaemitDir, "objects/$lastCommitHash")
    val lastCommitContent = if (lastCommitFile.exists() && lastCommitFile.isFile) {
        lastCommitFile.readText()
    } else {
        ""
    }

    if (fileToAdd.name.contains(".chaemit") || fileToAdd.name.contains(".DS_Store")) {
        return
    }

    if (fileToAdd.isDirectory) {
        val currentFiles = fileToAdd.walk().map { getRelativePath(it, chaemitDir) }.toSet()
        fileToAdd.walk().forEach { file ->
            addFileOrDirectory(file, chaemitDir, lastCommitContent)
        }

        val lastCommitFiles = getLastCommitPaths(lastCommitContent).toSet()
        lastCommitFiles.subtract(currentFiles).forEach { filePath ->
            removeFromIndex(filePath, chaemitDir)
        }
    } else {
        addFileOrDirectory(fileToAdd, chaemitDir, lastCommitContent)
    }
}

private fun removeFromIndex(filePath: String, chaemitDir: String) {
    val indexFile = File(chaemitDir, "index")
    if (!indexFile.exists()) {
        println("Index file not found")
        return
    }

    val lines = indexFile.readLines().toMutableList()
    val relativePath = getRelativePath(File(filePath), chaemitDir)

    lines.removeAll { it.startsWith(relativePath) }

    indexFile.writeText(lines.joinToString("\n"))
}

private fun getLastCommitPaths(lastCommitContent: String): Set<String> {
    val files = mutableSetOf<String>()
    val lines = lastCommitContent.split("\n")

    var changesSection = false
    for (line in lines) {
        if (line.startsWith("Changes")) {
            changesSection = true
            continue
        }
        if (changesSection && line.isNotBlank()) {
            val parts = line.split(" ")
            if (parts.size >= 2) {
                files.add(parts[0])
            }
        }
    }

    return files
}

fun checkWorkingDirectoryChanges(chaemitDir: String): Boolean {
    val headFile = File(chaemitDir, "HEAD")
    val currentBranch = headFile.readText().trim().split(": ").last()
    val branchFile = File(chaemitDir, currentBranch)
    val lastCommitHash = branchFile.readText().trim()
    val lastCommitFile = File(chaemitDir, "objects/$lastCommitHash")
    val lastCommitContent = if (lastCommitFile.exists() && lastCommitFile.isFile) {
        lastCommitFile.readText()
    } else {
        ""
    }

    if (lastCommitContent.isBlank()) {
        return true
    }

    val lastCommitStates = getLastCommitState(lastCommitHash, chaemitDir)
    val currentDirState = getCurrentWorkingDirectoryState(File(System.getProperty("user.dir")), chaemitDir)

    val changes = mutableSetOf<String>()

    currentDirState.forEach { (filePath, checksum) ->
        if(!filePath.contains(".chaemit") && !filePath.contains(".DS_Store")) {
            val lastCommitChecksum = lastCommitStates[filePath]
            println("filePath: $filePath")
            println("checksum: $checksum, lastChecksum: $lastCommitChecksum")
            println()
            if (checksum != lastCommitChecksum) {
                changes.add(filePath)
            }
        }
    }

    if (changes.isNotEmpty()) {
        println("\u001B[31mThere are uncommitted changes in the following files:\u001B[0m")
        changes.forEach { println("\u001B[31m$it\u001B[0m") }
        return false
    }
    return true
}

fun getCurrentWorkingDirectoryState(directory: File, chaemitDir: String): Map<String, String> {
    val filesState = mutableMapOf<String, String>()

    directory.walk().forEach { file ->
        if (file.isFile) {
            val relativePath = file.relativeTo(directory).path
            filesState[relativePath] = createChecksum(relativePath)
        } else {
            val relativePath = getRelativePath(file, chaemitDir)
            filesState[relativePath] = createChecksumForDirectory(file)
        }
    }

    return filesState
}


private fun getLastCommitState(lastCommitHash: String, chaemitDir: String): Map<String, String> {
    val (rootTreePath, rootTreeChecksum) = getCommitRootTree(lastCommitHash, chaemitDir)
    val lastCommitStates = mutableMapOf<String, String>()
    lastCommitStates[rootTreePath] = rootTreeChecksum
    walkLastCommit(rootTreePath, rootTreeChecksum, lastCommitStates, chaemitDir)
    return lastCommitStates
}

private fun walkLastCommit(treePath: String, treeHash: String, lastCommitStates: MutableMap<String, String>, chaemitDir: String) {
    val treeFile = File("$chaemitDir/objects/$treeHash")
    val treeContents = treeFile.readText()

    if (treeContents.isNotBlank()) {
        treeContents.lines().forEach { line ->
            val parts = line.split(" ")
            val path = parts[0]
            val hash = parts[1]
            val type = parts[2]

            lastCommitStates[path] = hash
            if (type == "tree") {
                walkLastCommit(path, hash, lastCommitStates, chaemitDir)
            }
        }
    }
}


fun showStatus(chaemitDir: String) {
    val indexFile = File(chaemitDir, "index")
    if (!indexFile.exists()) {
        println("No changes.")
        return
    }

    val indexLines = indexFile.readLines()
    val indexedFileStatus = indexLines.map { it.split(" ") }
        .associate { it[0] to it[3] }

    println("\u001B[32mStaged Changes: \u001B[0m")
    indexLines.forEach { line ->
        val parts = line.split(" ")
        if (parts.size >= 4) {
            val filePath = parts[0]
            val status = parts[3]
            println("\u001B[32m$filePath ($status)\u001B[0m")
        }
    }

    val currentDir = File(System.getProperty("user.dir"))
    val currentFilesChecksums = getCurrentFilesChecksums(currentDir)

    println("\n\u001B[31mUnstaged changes: \u001B[0m")
    currentFilesChecksums.forEach { (filePath, checksum) ->
        if (!filePath.contains(".chaemit") && !indexedFileStatus.containsKey(filePath)) {
            println("\u001B[31m$filePath\u001B[0m")
        }
    }
}

fun getCurrentFilesChecksums(dir: File): Map<String, String> {
    val filesChecksums = mutableMapOf<String, String>()
    dir.walk().forEach { file ->
        if (file.isFile) {
            val checksum = createChecksum(file.path)
            filesChecksums[file.path] = checksum
        }
    }
    return filesChecksums
}

fun getCurrentFiles(dir: File): List<String> {
    val files = mutableListOf<String>()

    if(dir.isDirectory) {
        dir.listFiles()?.forEach { file ->
            if (!file.absolutePath.contains("${File.separator}.chaemit")) {
                if (file.isDirectory) {
                    files.addAll(getCurrentFiles(file))
                } else {
                    files.add(file.path)
                }
            }
        }
    }

    return files
}

fun updateIndex(filePath: String, chaemitDir: String, checksum: String, type: String) {
    val indexFile = File(chaemitDir, "index")
    val lines = if (indexFile.exists()) indexFile.readLines() else mutableListOf()

    val newLine = "$filePath $checksum $type"
    var found = false
    var changed = false

    val updatedLines = lines.map {
        if (it.startsWith("$filePath ")) {
            found = true
            if (it == newLine) {
                return
            }
            changed = true
            newLine
        } else it
    }.toMutableList()

    if (!found) {
        updatedLines.add(newLine)
        println(newLine)
        changed = true
    }

    if (changed) {
        indexFile.writeText(updatedLines.joinToString("\n"))
        //if (type == "blob")
        println("\u001B[32mAdded '$filePath' to index.\u001B[0m")
    }
}

private val checksumCache = mutableMapOf<String, String>()

private fun processDirectoryChecksum(directory: File, chaemitDir: String, lastCommitContent: String): List<String> {
    val entries = mutableListOf<String>()

    directory.listFiles()
        ?.filter { it.name != ".chaemit" && it.name != ".DS_Store" }
        ?.sortedWith(compareBy { it.name })
        ?.forEach { subFile ->
            val subFileType = if (subFile.isDirectory) "tree" else "blob"
            val subFilePath = getRelativePath(subFile, chaemitDir)
            val subFileChecksum = checksumCache.getOrPut(subFilePath) {
                if (subFile.isDirectory) createChecksumForDirectory(subFile) else createChecksum(subFilePath)
            }
            entries.add("$subFilePath $subFileChecksum $subFileType")
        }

    return entries
}

private fun createChecksumForDirectory(directory: File): String {
    val directoryEntries = processDirectoryChecksum(directory, System.getProperty("user.dir") + "/.chaemit", "")
    val directoryChecksum = createChecksumForInput(directoryEntries.joinToString("\n"))
    checksumCache[directory.absolutePath] = directoryChecksum
    return directoryChecksum
}

private fun addFileOrDirectory(file: File, chaemitDir: String, lastCommitContent: String) {
    if (file.absolutePath.contains(".chaemit") || file.absolutePath.contains(".DS_Store")) {
        return
    }
    if (file.isDirectory) {
        val treeEntries = processDirectoryChecksum(file, chaemitDir, lastCommitContent)
        val treeChecksum = createChecksumForInput(treeEntries.joinToString("\n"))

        val filePath = getRelativePath(file, chaemitDir)
        if (isFileChanged(filePath, treeChecksum, "tree", lastCommitContent)) {
            updateIndex(filePath, chaemitDir, treeChecksum, "tree")
            saveTreeObject(file.name, chaemitDir, treeChecksum, treeEntries)
        }
    } else {
        val filePath = getRelativePath(file, chaemitDir)
        val checksum = createChecksum(filePath)
        if (isFileChanged(filePath, checksum, "blob", lastCommitContent)) {
            updateIndex(filePath, chaemitDir, checksum, "blob")
            saveBlobObject(filePath, chaemitDir, checksum)
        }
    }
}

private fun isFileChanged(filePath: String, checksum: String, type: String, lastCommitContent: String): Boolean {
    // 파일 경로와 체크섬을 조합한 문자열 생성
    val fileEntry = "$filePath $checksum $type"

    // lastCommitContent에서 해당 파일에 대한 기록 찾기
    val lines = lastCommitContent.lines()
    val fileLine = lines.find { it.split(" ").first() == filePath }

    return when {
        fileLine == null -> true // 파일이 새로 추가됨
        fileLine != fileEntry -> true // 파일의 체크섬이 변경됨 (수정됨)
        else -> false // 변경 없음
    }
}

private fun getRelativePath(file: File, chaemitDir: String): String {
    val rootDir = File(chaemitDir).parentFile
    return rootDir.toURI().relativize(file.toURI()).path
}

fun resetIndex(chaemitDir: String, filePath: String? = null) {
    val indexFile = File(chaemitDir, "index")
    if (filePath == null) {
        PrintWriter(indexFile).use { writer -> writer.print("") }
        println("Reset complete. All staged changes have been removed.")
    } else {
        val lines = indexFile.readLines().filterNot { it.startsWith("$filePath ") }
        PrintWriter(indexFile).use { writer ->
            lines.forEach { writer.println(it) }
        }
        println("Reset complete. Removed '$filePath' from staging area.")
    }
}

fun commitChanges(chaemitDir: String, commitMessage: String) {
    val objectsDir = File(chaemitDir, "objects")
    if (!objectsDir.exists())
        throw IllegalStateException("objects directory not found")

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val currentDate = dateFormat.format(Date())
    val commitContent = StringBuilder()

    // 부모 커밋 가져오기
    val headFile = File(chaemitDir, "HEAD")
    val currentBranch = headFile.readText().trim().split(":").last().trim()
    val branchFile = File(chaemitDir, currentBranch)
    val parentCommitHash = if (branchFile.exists()) branchFile.readText().trim() else ""

    commitContent.append("Date: ").append(currentDate).append("\n")
    commitContent.append("Message: ").append(commitMessage).append("\n")
    if (parentCommitHash.isNotEmpty()) {
        commitContent.append("Parent: ").append(parentCommitHash).append("\n")
    }

    val indexFile = File(chaemitDir, "index")
    if (indexFile.exists()) {
        val lines = indexFile.readLines()
        commitContent.append("Changes:\n")
        lines.forEach { commitContent.append(it).append("\n") }
    }

    val commitHash = createChecksumForInput(commitContent.toString())
    val commitFile = File(objectsDir, commitHash)
    commitFile.writeText(commitContent.toString())


    if (!branchFile.exists()) {
        branchFile.parentFile.mkdirs()
        branchFile.createNewFile()
    }
    branchFile.writeText(commitHash)

    indexFile.writeText("")

    println("Created commit: $commitHash")
}

fun createBranch(branchName: String, chaemitDir: String) {
    val headsDir = File(chaemitDir, "refs/heads")
    val newBranchFile = File(headsDir, branchName)

    val headFile = File(chaemitDir, "HEAD")
    val currentCommitHash = File(chaemitDir, headFile.readText().trim().split(": ").last()).readText().trim()

    if (!newBranchFile.exists()) {
        newBranchFile.writeText(currentCommitHash)
    }

    headFile.writeText("ref: refs/heads/$branchName")
    println("Switched to a new branch '$branchName'")
}

fun showBranches(chaemitDir: String) {
    val headsDir = File(chaemitDir, "refs/heads")
    val branches = headsDir.listFiles()?.map { it.name } ?: listOf()

    val headFile = File(chaemitDir, "HEAD")
    val currentBranch = if (headFile.exists()) headFile.readText().trim().split("/").last() else "unknown"

    println("Branches:")
    branches.forEach { branch ->
        if (branch == currentBranch) {
            println("\u001B[32m*  $branch\u001B[0m")
        } else {
            println("   $branch")
        }
    }
}

fun checkoutBranch(branchName: String, chaemitDir: String) {
    val headsDir = File(chaemitDir, "refs/heads")
    val headFile = File(chaemitDir, "HEAD")

    val currentBranch = if (headFile.exists()) headFile.readText().trim().split("/").last() else "unknown"
    val switchedBranch = File("$headsDir/$branchName")

    if (!switchedBranch.exists()) {
        println("Branch $branchName does not exist.")
        return
    }
    if (currentBranch == branchName) {
        println("Already on branch $branchName.")
        return
    }

    if (!checkWorkingDirectoryChanges(chaemitDir)) {
        return
    }

    // 커밋 가져와서 복원하기
    val lastCommitHash = switchedBranch.readText().trim()
    val (path, checksum) = getCommitRootTree(lastCommitHash, chaemitDir)
    restoreWorkingDirectory(path, checksum, chaemitDir)

    headFile.writeText("refs/heads/$branchName")
    println("Switched to branch $branchName")
}

private fun getCommitRootTree(commitHash: String, chaemitDir: String): Pair<String, String> {
    val commitFile = File(chaemitDir, "objects/$commitHash")
    if (!commitFile.exists()) {
        throw IllegalStateException("Commit object does not exist.")
    }

    val commitContent = commitFile.readText()
    val changesSection = commitContent.split("Changes:\n").getOrElse(1) { "" }
    val firstLineOfChanges = changesSection.lines().firstOrNull() ?: ""
    val parts = firstLineOfChanges.split(" ")
    val path = parts.getOrNull(0)?: ""
    val checksum = parts.getOrNull(1) ?: ""

    return Pair(path, checksum)
}

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

            unzip(zipFilePath, "$chaemitDir/objects", chaemitDir)
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
    restoreWorkingDirectory(path, checksum, chaemitDir)

}

private fun applyChanges(changesSection: String, chaemitDir: String) {
    val workingDir = File(System.getProperty("user.dir"))

    changesSection.lines().reversed().forEach { line ->
        if (line.isBlank()) return@forEach

        val parts = line.split(" ")
        val filePath = parts[0]
        val fileHash = parts[1]
        val fileType = parts[2]
        val fileStatus = parts[3]

        val targetFile = File(workingDir, filePath)

        when (fileStatus) {
            "deleted" -> {
                if (targetFile.exists()) {
                    targetFile.delete()
                    println("\u001B[31mDeleted: $filePath\u001B[0m")
                }
            }
            "added", "modified" -> {
                if (fileType == "blob") {
                    val fileData = readBlobData("$chaemitDir/objects", fileHash)
                    val isModified = targetFile.exists()
                    targetFile.apply {
                        parentFile.mkdirs()
                        writeText(fileData)
                    }
                    if (isModified) {
                        println("\u001B[32mModified: $filePath\u001B[0m")
                    } else {
                        println("\u001B[32mAdded: $filePath\u001B[0m")
                    }
                } else if (fileType == "tree") {
                    targetFile.mkdirs()
                }
            }
        }
    }
}

fun readBlobData(objectsDir: String, fileHash: String): String {
    val fileDirName = fileHash.substring(0, 2)
    val fileFileName = fileHash.substring(2)
    val file = File("$objectsDir/$fileDirName/$fileFileName")

    FileInputStream(file).use { fis ->
        InflaterInputStream(fis).use { iis ->
            ByteArrayOutputStream().use { baos ->
                iis.copyTo(baos)
                return baos.toString(Charsets.UTF_8.name())
            }
        }
    }
}

private fun unzip(zipFilePath: String, targetDirectory: String, chaemitDir: String) {
    val buffer = ByteArray(1024)
    ZipInputStream(FileInputStream(zipFilePath)).use { zis ->
        var zipEntry = zis.nextEntry

        while (zipEntry != null) {
            val isBranchFile = zipEntry.name.startsWith("refs/heads/") // 브랜치 파일인지 확인
            val targetPath = if (isBranchFile) chaemitDir else targetDirectory
            val newFile = File(targetPath, zipEntry.name)

            if (zipEntry.isDirectory) {
                newFile.mkdirs()
            } else {
                val parentDir = newFile.parentFile
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }

                FileOutputStream(newFile).use { fos ->
                    var len: Int
                    while (zis.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                }
            }

            zipEntry = zis.nextEntry
        }
    }
}


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

fun readCommitChanges(chaemitDir: String): List<Triple<String, String, String>> {
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

fun restoreWorkingDirectory(rootTreePath: String, rootTreeHash: String, chaemitDir: String) {
    val restoredPaths = mutableSetOf<String>()
    restoreTree(rootTreePath, rootTreeHash, chaemitDir, System.getProperty("user.dir"), restoredPaths)
}

fun restoreTree(treePath: String, treeHash: String, chaemitDir: String, targetDir: String, restoredPaths: MutableSet<String>) {
    val treeFile = File("$chaemitDir/objects/$treeHash")
    val treeContents = treeFile.readText()

    // 현재 워킹 디렉토리의 초기 상태 기록
    var initialDirState = File(targetDir, treePath).listFiles()?.map { it.name }?.toSet() ?: emptySet()

    if (treeContents.isNotBlank()) {
        treeContents.lines().forEach { line ->
            val parts = line.split(" ")
            val path = parts[0]
            val hash = parts[1]
            val type = parts[2]

            val fullPath = File(targetDir, path).absolutePath
            if (type == "blob") {
                val blobDirName = hash.substring(0, 2)
                val blobFileName = hash.substring(2)
                val blobFilePath = "$chaemitDir/objects/$blobDirName/$blobFileName"


                val fileData = readBlobData("$chaemitDir/objects", hash)
                File(fullPath).apply {
                    parentFile.mkdirs()
                    writeText(fileData)
                    restoredPaths.add(fullPath)
                }
            } else if (type == "tree") {
                val newDir = File(fullPath)
                newDir.mkdir()
                restoredPaths.add(fullPath)

                restoreTree(path, hash, chaemitDir, targetDir, restoredPaths)
            }
        }
    }

    // 현재 워킹 디렉토리와 복원된 디렉토리 비교
    val restoredNames = restoredPaths.map {
        File(it).relativeTo(File(targetDir, treePath)).path
    }.filter {
        // 타겟 디렉토리 내의 경로만 필터링
        it.isNotBlank() && it != ".." && it.startsWith("./") || !it.contains("/")
    }.map {
        // 파일 또는 디렉토리 이름만 추출
        it.removePrefix("./")
    }.toSet()
//    println("current directory: $targetDir")
//    println("initialDirState: $initialDirState")
//    println("restoredNames: $restoredNames")

    initialDirState.subtract(restoredNames).forEach { deletedName ->
        val deletedFile = File("$targetDir/$treePath/$deletedName")
        //println("deletedName: ${deletedFile.path}")

        if (deletedFile.isFile) {
            deletedFile.delete()
            println("\u001B[31mDeleted: ${deletedFile.path}\u001B[0m")
        }
    }
}


fun main(args: Array<String>) {
    val chaemitDir = System.getProperty("user.dir") + "/.chaemit"
    FuelManager.instance.apply {
        baseHeaders = mapOf("Content-Type" to "application/json")
    }

    if (args.isNotEmpty()) {
        when (args[0]) {
            "remote" -> {
                addRemoteRepository(chaemitDir, args[3])
            }
            "init" -> {
                initializeChaemitRepository()
            }
            "add" -> {
                addToStagingArea(args[1], chaemitDir)
            }
            "reset" -> {
                val filePath = if (args.size > 2) args[2] else null
                resetIndex(chaemitDir, filePath)
            }
            "status" -> {
                showStatus(chaemitDir)
            }
            "commit" -> {
                commitChanges(chaemitDir, args[2])
            }
            "branch" -> {
                if (args.size > 1) {
                    val branchName = args[1]
                    createBranch(branchName, chaemitDir)
                } else {
                    showBranches(chaemitDir)
                }
            }
            "checkout" -> {
                if (args.size > 1) {
                    val branchName = args[1]
                    checkoutBranch(branchName, chaemitDir)
                }
            }
            "push" -> {
                push(chaemitDir)
            }
            "pull" -> {
                if (args.size > 3) {
                    pull(chaemitDir, args[3])
                } else {
                    pull(chaemitDir, "main")
                }
            }
            else -> {
                println("Unknown command: ${args[0]}")
            }
        }
    } else {
        println("No arguments provided.")
    }
}