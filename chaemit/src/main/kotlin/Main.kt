
import operation.*
import java.io.*

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

fun main(args: Array<String>) {
    val chaemitDir = System.getProperty("user.dir") + "/.chaemit"

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
            "stash" -> {
                if (args.size > 1)
                    stashPop(chaemitDir)
                else
                    stash(chaemitDir)
            }
            else -> {
                println("Unknown command: ${args[0]}")
            }
        }
    } else {
        println("No arguments provided.")
    }
}