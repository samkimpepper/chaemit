package util

import util.ChecksumUtils.Companion.createChecksum
import java.io.File

class StateUtils {
    companion object {
        fun isFileChanged(filePath: String, checksum: String, type: String, lastCommitContent: String): Boolean {
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

        fun getCurrentBranchName(chaemitDir: String): String {
            val headsDir = File(chaemitDir, "refs/heads")
            val headFile = File(chaemitDir, "HEAD")

            val currentBranch = if (headFile.exists()) headFile.readText().trim().split("/").last() else "unknown"
            return currentBranch
        }

        fun getCommitRootTree(commitHash: String, chaemitDir: String): Pair<String, String> {
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

        fun getLastCommitPaths(lastCommitContent: String): Set<String> {
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

        fun getLastCommitHash(chaemitDir: String): String {
            val headFile = File(chaemitDir, "HEAD")
            val currentBranch = headFile.readText().trim().split(": ").last()
            val branchFile = File(chaemitDir, currentBranch)
            return branchFile.readText().trim()
        }

        fun getLastCommitState(chaemitDir: String): Map<String, String> {
            val lastCommitHash = getLastCommitHash(chaemitDir)
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

        fun getCurrentWorkingDirectoryState(directory: File, chaemitDir: String): Map<String, String> {
            val filesState = mutableMapOf<String, String>()

            directory.walk().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(directory).path
                    filesState[relativePath] = createChecksum(relativePath)
                } else {
                    val relativePath = FileUtils.getRelativePath(file, chaemitDir)
                    filesState[relativePath] = ChecksumUtils.createChecksumForDirectory(file)
                }
            }

            return filesState
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
    }
}