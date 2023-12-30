package operation

import util.ChecksumUtils
import java.io.File
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*

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

    val commitHash = ChecksumUtils.createChecksumForInput(commitContent.toString())
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