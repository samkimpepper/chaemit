package operation

import util.ChecksumUtils
import util.ChecksumUtils.Companion.createChecksumForInput
import util.FileUtils
import util.IndexUtils
import util.StateUtils
import java.io.File

fun addToStagingArea(target: String, chaemitDir: String) {
    val fileToAdd = if (target == ".") {
        File(System.getProperty("user.dir")).absoluteFile
    } else {
        File(target).absoluteFile
    }

    val lastCommitHash = StateUtils.getLastCommitHash(chaemitDir)
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
        val currentFiles = fileToAdd.walk().map { FileUtils.getRelativePath(it, chaemitDir) }.toSet()
        fileToAdd.walk().forEach { file ->
            addFileOrDirectory(file, chaemitDir, lastCommitContent)
        }

        val lastCommitFiles = StateUtils.getLastCommitPaths(lastCommitContent).toSet()
        lastCommitFiles.subtract(currentFiles).forEach { filePath ->
            IndexUtils.removeFromIndex(filePath, chaemitDir)
        }
    } else {
        addFileOrDirectory(fileToAdd, chaemitDir, lastCommitContent)
    }
}

private fun addFileOrDirectory(file: File, chaemitDir: String, lastCommitContent: String) {
    if (file.absolutePath.contains(".chaemit") || file.absolutePath.contains(".DS_Store")) {
        return
    }
    if (file.isDirectory) {
        val treeEntries = ChecksumUtils.processDirectoryChecksum(file, chaemitDir, lastCommitContent)
        val treeChecksum = createChecksumForInput(treeEntries.joinToString("\n"))

        val filePath = FileUtils.getRelativePath(file, chaemitDir)
        if (StateUtils.isFileChanged(filePath, treeChecksum, "tree", lastCommitContent)) {
            IndexUtils.updateIndex(filePath, chaemitDir, treeChecksum, "tree")
            FileUtils.saveTreeObject(file.name, chaemitDir, treeChecksum, treeEntries)
        }
    } else {
        val filePath = FileUtils.getRelativePath(file, chaemitDir)
        val checksum = ChecksumUtils.createChecksum(filePath)
        if (StateUtils.isFileChanged(filePath, checksum, "blob", lastCommitContent)) {
            IndexUtils.updateIndex(filePath, chaemitDir, checksum, "blob")
            FileUtils.saveBlobObject(filePath, chaemitDir, checksum)
        }
    }
}