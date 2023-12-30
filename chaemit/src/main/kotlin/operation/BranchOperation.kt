package operation

import util.FileUtils
import util.StateUtils
import java.io.File

fun createBranch(branchName: String, chaemitDir: String) {
    val headsDir = File(chaemitDir, "refs/heads")
    val newBranchFile = File(headsDir, branchName)

    val headFile = File(chaemitDir, "HEAD")
    val currentCommitHash = File(chaemitDir, headFile.readText().trim().split(": ").last()).readText().trim()

    if (!newBranchFile.exists()) {
        newBranchFile.writeText(currentCommitHash)
    }

    headFile.writeText("ref: refs/heads/$branchName")
    println("Switched to a new branch '$branchName'")
}

fun showBranches(chaemitDir: String) {
    val headsDir = File(chaemitDir, "refs/heads")
    val branches = headsDir.listFiles()?.map { it.name } ?: listOf()

    val headFile = File(chaemitDir, "HEAD")
    val currentBranch = if (headFile.exists()) headFile.readText().trim().split("/").last() else "unknown"

    println("Branches:")
    branches.forEach { branch ->
        if (branch == currentBranch) {
            println("\u001B[32m*  $branch\u001B[0m")
        } else {
            println("   $branch")
        }
    }
}

fun checkoutBranch(branchName: String, chaemitDir: String) {
    val headsDir = File(chaemitDir, "refs/heads")
    val headFile = File(chaemitDir, "HEAD")

    val currentBranch = if (headFile.exists()) headFile.readText().trim().split("/").last() else "unknown"
    val switchedBranch = File("$headsDir/$branchName")

    if (!switchedBranch.exists()) {
        println("Branch $branchName does not exist.")
        return
    }
    if (currentBranch == branchName) {
        println("Already on branch $branchName.")
        return
    }

    if (!checkWorkingDirectoryChanges(chaemitDir)) {
        return
    }

    // 커밋 가져와서 복원하기
    val lastCommitHash = switchedBranch.readText().trim()
    val (path, checksum) = StateUtils.getCommitRootTree(lastCommitHash, chaemitDir)
    FileUtils.restoreWorkingDirectory(path, checksum, chaemitDir)

    headFile.writeText("refs/heads/$branchName")
    println("Switched to branch $branchName")
}

private fun checkWorkingDirectoryChanges(chaemitDir: String): Boolean {
    val lastCommitStates = StateUtils.getLastCommitState(chaemitDir)
    val currentDirState = StateUtils.getCurrentWorkingDirectoryState(File(System.getProperty("user.dir")), chaemitDir)

    val changes = mutableSetOf<String>()

    currentDirState.forEach { (filePath, checksum) ->
        if(!filePath.contains(".chaemit") && !filePath.contains(".DS_Store")) {
            val lastCommitChecksum = lastCommitStates[filePath]
            println("filePath: $filePath")
            println("checksum: $checksum, lastChecksum: $lastCommitChecksum")
            println()
            if (checksum != lastCommitChecksum) {
                changes.add(filePath)
            }
        }
    }

    if (changes.isNotEmpty()) {
        println("\u001B[31mThere are uncommitted changes in the following files:\u001B[0m")
        changes.forEach { println("\u001B[31m$it\u001B[0m") }
        return false
    }
    return true
}