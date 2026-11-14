package com.example.callrecorder

import java.io.File

data class Recording(
    val file: File,
    val displayName: String,
    val dateMillis: Long,
    val sizeBytes: Long
)
