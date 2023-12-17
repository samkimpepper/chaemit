package com.example.chaemit.commit

import com.example.chaemit.branch.Branch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface CommitRepository: JpaRepository<Commit, Long> {
    fun findTopByBranchOrderByCreatedAtDesc(branch: Branch): Optional<Commit>
}