package com.example.chaemit.branch

import com.example.chaemit.repository.Repository
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne

@Entity
data class Branch(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    val name: String,

    @ManyToOne
    val repository: Repository,
)
