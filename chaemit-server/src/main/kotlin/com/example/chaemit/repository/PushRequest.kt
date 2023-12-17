package com.example.chaemit.repository

import org.springframework.web.multipart.MultipartFile

data class PushRequest(
    val commit: MultipartFile,
    val objects: MultipartFile,
)
