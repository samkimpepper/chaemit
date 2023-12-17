package com.example.chaemit.repository

import org.springframework.stereotype.Service

@Service
class RepositoryService(
    private val repositoryRepository: RepositoryRepository,
) {

    fun create(data: CreateRequest): Repository {
        val repository = repositoryRepository.findByName(data.name)
        if (repository.isPresent) {
            return repository.get()
        }
        val repositoryEntity = Repository(name=data.name)
        return repositoryRepository.save(repositoryEntity)
    }
}