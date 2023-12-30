package operation

import java.io.File

fun initializeChaemitRepository() {
    val currentDir = System.getProperty("user.dir")
    val chaemitDir = File(currentDir, ".chaemit")
    val objectsDir = File(chaemitDir, "objects")
    val refsDir = File(chaemitDir, "refs")
    val headsDir = File(refsDir, "heads")
    val headFile = File(chaemitDir, "HEAD")
    val repoFile = File(chaemitDir, "REPO")
    val mainBranchFile = File(headsDir, "main")

    if (!chaemitDir.exists())
        chaemitDir.mkdir()

    if (!objectsDir.exists())
        objectsDir.mkdir()

    if (!refsDir.exists())
        refsDir.mkdir()

    if (!headsDir.exists())
        headsDir.mkdir()

    if (!headFile.exists())
        headFile.writeText("ref: refs/heads/main")

    if (!repoFile.exists())
        repoFile.writeText("-1")

    if(!mainBranchFile.exists())
        mainBranchFile.createNewFile()

    println("Initialized empty Chaemit repository in ${chaemitDir.path}")
}