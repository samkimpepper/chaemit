package com.example.chaemit.branch

import com.example.chaemit.repository.Repository
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

@org.springframework.stereotype.Repository
interface BranchRepository: JpaRepository<Branch, Long> {
    fun findByRepositoryAndName(repository: Repository, name: String): Optional<Branch>
}