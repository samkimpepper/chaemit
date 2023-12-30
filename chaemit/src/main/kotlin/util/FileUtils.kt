package util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipInputStream

class FileUtils {
    companion object {
        fun getRelativePath(file: File, chaemitDir: String): String {
            val rootDir = File(chaemitDir).parentFile
            return rootDir.toURI().relativize(file.toURI()).path
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

        fun saveTreeObject(name: String, chaemitDir: String, checksum: String, entries: List<String>) {
            val treeObjectFile = File(chaemitDir, "objects/$checksum")
            treeObjectFile.writeText(entries.joinToString("\n"))
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

        fun unzip(zipFilePath: String, targetDirectory: String, chaemitDir: String) {
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
    }
}