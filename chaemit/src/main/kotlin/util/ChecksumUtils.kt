package util

import java.io.File
import java.security.MessageDigest

class ChecksumUtils {
    companion object {
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

        private val checksumCache = mutableMapOf<String, String>()

        fun processDirectoryChecksum(directory: File, chaemitDir: String, lastCommitContent: String): List<String> {
            val entries = mutableListOf<String>()

            directory.listFiles()
                ?.filter { it.name != ".chaemit" && it.name != ".DS_Store" }
                ?.sortedWith(compareBy { it.name })
                ?.forEach { subFile ->
                    val subFileType = if (subFile.isDirectory) "tree" else "blob"
                    val subFilePath = FileUtils.getRelativePath(subFile, chaemitDir)
                    val subFileChecksum = checksumCache.getOrPut(subFilePath) {
                        if (subFile.isDirectory) createChecksumForDirectory(subFile) else createChecksum(subFilePath)
                    }
                    entries.add("$subFilePath $subFileChecksum $subFileType")
                }

            return entries
        }

        fun createChecksumForDirectory(directory: File): String {
            val directoryEntries = processDirectoryChecksum(directory, System.getProperty("user.dir") + "/.chaemit", "")
            val directoryChecksum = createChecksumForInput(directoryEntries.joinToString("\n"))
            checksumCache[directory.absolutePath] = directoryChecksum
            return directoryChecksum
        }
    }
}