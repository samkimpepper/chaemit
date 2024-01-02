package operation

import util.ChecksumUtils
import util.FileUtils
import util.IndexUtils
import util.StateUtils
import java.io.File
import java.util.stream.StreamSupport

fun stash(chaemitDir: String) {
    val objectsDir = File(chaemitDir, "objects")
    val indexFile = File(chaemitDir, "index")
    if (!objectsDir.exists() || !indexFile.exists())
        throw IllegalStateException("Required directories or files not found")

    val stashContent = StringBuilder()
    stashContent.append("Working Directory Changes:\n")

    val lastCommitContent = StateUtils.getLastCommitContent(chaemitDir)
    val rootDir = File(System.getProperty("user.dir")).absoluteFile
    saveWorkingDirectoryState(rootDir, stashContent, lastCommitContent, chaemitDir)

    val indexChanges = indexFile.readText()
    if (indexChanges.isNotEmpty())
        stashContent.append("Index Changes:\n").append(indexChanges).append("\n")

    val stashHash = ChecksumUtils.createChecksumForInput(stashContent.toString())
    val stashFile = File(objectsDir, stashHash)
    stashFile.writeText(stashContent.toString())

    val stashRefFile = File(chaemitDir, "refs/stash")
    stashRefFile.appendText("$stashHash\n")

    indexFile.writeText("")

    val lastCommitHash = StateUtils.getLastCommitHash(chaemitDir)
    val lastCommitRootTree = StateUtils.getCommitRootTree(lastCommitHash, chaemitDir)
    FileUtils.restoreWorkingDirectory(lastCommitRootTree.first, lastCommitRootTree.second, chaemitDir)

    println("Created stash: $stashHash")
}

private fun saveWorkingDirectoryState(rootDir: File, stashContent: StringBuilder, lastCommitContent: String, chaemitDir: String) {
    rootDir.walk().forEach { file ->
        if (!file.name.contains(".chaemit") && !file.name.contains(".DS_Store")) {
            saveInternal(file, stashContent, chaemitDir, lastCommitContent)
        }
    }
}

private fun saveInternal(file: File, stashContent: StringBuilder, chaemitDir: String, lastCommitContent: String) {
    if (file.absolutePath.contains(".chaemit") || file.absolutePath.contains(".DS_Store")) {
        return
    }
    if (file.isDirectory) {
        val treeEntries = ChecksumUtils.processDirectoryChecksum(file, chaemitDir, lastCommitContent)
        val treeChecksum = ChecksumUtils.createChecksumForInput(treeEntries.joinToString("\n"))

        val filePath = FileUtils.getRelativePath(file, chaemitDir)
        if (StateUtils.isFileChanged(filePath, treeChecksum, "tree", lastCommitContent)) {
            stashContent.append("$filePath $treeChecksum tree\n")
            FileUtils.saveTreeObject(file.name, chaemitDir, treeChecksum, treeEntries)
        }
    } else {
        val filePath = FileUtils.getRelativePath(file, chaemitDir)
        val checksum = ChecksumUtils.createChecksum(filePath)
        if (StateUtils.isFileChanged(filePath, checksum, "blob", lastCommitContent)) {
            stashContent.append("$filePath $checksum blob\n")
            FileUtils.saveBlobObject(filePath, chaemitDir, checksum)
        }
    }
}

fun stashPop(chaemitDir: String) {
    val stashRefFile = File(chaemitDir, "refs/stash")
    if (!stashRefFile.exists() || stashRefFile.readText().isEmpty())
        throw IllegalStateException("No stashes to pop")

    val stashList = stashRefFile.readLines()
    val latestStashHash = stashList.last()

    val stashFile = File(chaemitDir, "objects/$latestStashHash")
    if (!stashFile.exists())
        throw IllegalStateException("Stash file not found")

    val stashContent = stashFile.readText()
    val lines = stashContent.lines()
    val rootTreeLine = lines.firstOrNull { it.startsWith("Working Directory Changes:") }
        ?.let { lines[lines.indexOf(it) + 1] }
    val rootTreeChecksum = rootTreeLine?.let { it.trim().split(" ")[0] }
        ?: throw IllegalStateException("Root tree checksum not found in stash")

    if (!checkStashPopConflicts(chaemitDir, rootTreeChecksum)) {
        return
    }
    FileUtils.restoreWorkingDirectory("", rootTreeChecksum, chaemitDir)

    val updatedStashList = stashList.dropLast(1)
    stashRefFile.writeText(updatedStashList.joinToString("\n"))

    println("Applied and dropped stash: $latestStashHash")
}

private fun checkStashPopConflicts(chaemitDir: String, stashrootTreeChecksum: String): Boolean {
    val currentDirStates = StateUtils.getCurrentWorkingDirectoryState(File(System.getProperty("user.dir")), chaemitDir)
    val stashStates = getStashState(chaemitDir, stashrootTreeChecksum)

    var conflictDetected = false

    stashStates.forEach { (path, stashChecksum) ->
        val currentChecksum = currentDirStates[path]
        if (currentChecksum != null && currentChecksum != stashChecksum) {
            println("\u001B[31mConflict detected in file: $path\u001B[0m")
            conflictDetected = true
        }
    }

    return conflictDetected
}

private fun getStashState(chaemitDir: String, stashRootTreeChecksum: String): Map<String, String> {
    val stashStates = mutableMapOf<String, String>()
    stashStates[""] = stashRootTreeChecksum
    walkStash(stashRootTreeChecksum, stashStates, chaemitDir)
    return stashStates
}

private fun walkStash(treeHash: String, stashStates: MutableMap<String, String>, chaemitDir: String) {
    val treeFile = File("$chaemitDir/objects/$treeHash")
    val treeContents = treeFile.readText()

    if (treeContents.isNotBlank()) {
        treeContents.lines().forEach { line ->
            val parts = line.split(" ")
            val path = parts[0]
            val hash = parts[1]
            val type = parts[2]

            stashStates[path] = hash
            if (type == "tree") {
                walkStash(hash, stashStates, chaemitDir)
            }
        }
    }
}