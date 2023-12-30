package operation

import util.StateUtils
import java.io.File

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
    val currentFilesChecksums = StateUtils.getCurrentFilesChecksums(currentDir)

    println("\n\u001B[31mUnstaged changes: \u001B[0m")
    currentFilesChecksums.forEach { (filePath, checksum) ->
        if (!filePath.contains(".chaemit") && !indexedFileStatus.containsKey(filePath)) {
            println("\u001B[31m$filePath\u001B[0m")
        }
    }
}