package operation

import java.io.File
import java.io.PrintWriter

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