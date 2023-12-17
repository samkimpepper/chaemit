package com.example.chaemit.repository

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

@org.springframework.stereotype.Repository
interface RepositoryRepository: JpaRepository<Repository, Long> {
    fun findByName(name: String): Optional<Repository>
}