package util

import java.io.File

class IndexUtils {
    companion object {
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

        fun removeFromIndex(filePath: String, chaemitDir: String) {
            val indexFile = File(chaemitDir, "index")
            if (!indexFile.exists()) {
                println("Index file not found")
                return
            }

            val lines = indexFile.readLines().toMutableList()
            val relativePath = FileUtils.getRelativePath(File(filePath), chaemitDir)

            lines.removeAll { it.startsWith(relativePath) }

            indexFile.writeText(lines.joinToString("\n"))
        }
    }
}