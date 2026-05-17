package com.sokind.chat.domain.timeline

import java.time.Instant

data class MessageView(
    val seq: Long,
    val userId: String,
    val clientTs: Instant,
    val payload: Map<String, Any?>,
    val deleted: Boolean,
    val editedAtSeq: Long?,
)
