package com.example.chaemit.branch

import com.amazonaws.services.kms.model.NotFoundException
import com.example.chaemit.S3UploadService
import com.example.chaemit.commit.Commit
import com.example.chaemit.commit.CommitRepository
import com.example.chaemit.repository.Repository
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class BranchService(
    private val branchRepository: BranchRepository,
    private val commitRepository: CommitRepository,
    private val s3UploadService: S3UploadService,
) {

    fun save(repository: Repository, branchName: String, commitFile: MultipartFile, objects: MultipartFile) {
        val branchOptional = branchRepository.findByRepositoryAndName(repository, branchName)
        var branch: Branch? = null
        if (!branchOptional.isPresent) {
            branch = Branch(
                name = branchName,
                repository = repository,
            )
            branchRepository.save(branch)
        } else {
            branch = branchOptional.get()
        }
        val fileZipLink = s3UploadService.savefile(objects)
        val commit = parseCommitFile(commitFile, fileZipLink, branch)
        commitRepository.save(commit)
    }

    fun get(repository: Repository, branchName: String): ByteArray {
        val branch = branchRepository.findByRepositoryAndName(repository, branchName).orElseThrow()
        val commit = commitRepository.findTopByBranchOrderByCreatedAtDesc(branch).orElseThrow()
        val objects = s3UploadService.getFile(commit.fileZipLink)
        return objects
    }

    private fun parseCommitFile(commitFile: MultipartFile, fileZipLink: String, branch: Branch): Commit {
        val commitData = String(commitFile.bytes)
        val lines = commitData.lines()
        val dateLine = lines.firstOrNull { it.startsWith("Date: ") }
        val messageLine = lines.firstOrNull { it.startsWith("Message: ") }
        val changes = lines.dropWhile { !it.startsWith("Changes: ") }.drop(1)

        val createdAt = LocalDateTime.parse(dateLine?.substringAfter("Date: ")?.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val message = messageLine?.substringAfter("Message: ")?.trim() ?: ""
        val content = changes.joinToString("\n") { parseChange(it) }

        val checksum = commitFile.originalFilename

        return Commit(
            createdAt = createdAt,
            message = message,
            content = content,
            branch = branch,
            checksum = checksum!!,
            fileZipLink = fileZipLink,
        )
    }

    private fun parseChange(change: String): String {
        val parts = change.split(" ")
        val filePath = parts[0]
        val action = when (parts.last()) {
            "added" -> "+++++"
            "modified" -> "*****"
            else -> "-----"
        }
        return "$filePath $action"
    }
}