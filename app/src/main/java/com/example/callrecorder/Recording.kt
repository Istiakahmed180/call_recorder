package com.example.callrecorder

import java.io.File

data class Recording(
    val file: File,
    val displayName: String,
    val dateMillis: Long,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val kind: String = KIND_OTHER
) {
    companion object {
        const val KIND_INCOMING = "Incoming"
        const val KIND_OUTGOING = "Outgoing"
        const val KIND_MISSED = "Missed"
        const val KIND_WHATSAPP = "WhatsApp"
        const val KIND_IMO = "IMO"
        const val KIND_CALL = "Call"
        const val KIND_OTHER = "Other"
    }
}
