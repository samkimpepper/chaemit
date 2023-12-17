package com.example.chaemit.commit

import com.example.chaemit.branch.Branch
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

@Entity
data class Commit(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    val checksum: String,

    val content: String,

    val message: String,

    val fileZipLink: String,

    val createdAt: LocalDateTime,

    @ManyToOne
    val branch: Branch,
) {
}