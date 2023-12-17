package com.example.chaemit.repository

import com.amazonaws.services.kms.model.NotFoundException
import com.example.chaemit.branch.BranchService
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/repositories")
class RepositoryController(
    private val repositoryService: RepositoryService,
    private val branchService: BranchService,
    private val repositoryRepository: RepositoryRepository,
) {

    @PostMapping
    fun remote(@RequestBody createRequest: CreateRequest): ResponseEntity<Long> {
        val repository = repositoryService.create(createRequest)
        return ResponseEntity.ok(repository.id)
    }

    @PostMapping("/{repositoryId}/branches/{branchName}")
    fun push(@RequestParam("commit") commit: MultipartFile,
             @RequestParam("objects") objects: MultipartFile,
             @PathVariable repositoryId: Long,
             @PathVariable branchName: String): ResponseEntity<Any> {
        val repository = repositoryRepository.findById(repositoryId).orElseThrow()

        branchService.save(repository, branchName, commit, objects)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/{repositoryId}/branches/{branchName}")
    fun pull(@PathVariable repositoryId: Long,
             @PathVariable branchName: String): ResponseEntity<Any> {
        val repository = repositoryRepository.findById(repositoryId).orElseThrow()
        val data = branchService.get(repository, branchName)

        val headers = HttpHeaders()
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$branchName.zip\"")
        headers.add(HttpHeaders.CONTENT_TYPE, "application/zip")

        return ResponseEntity
            .ok()
            .headers(headers)
            .body(data)
    }
}