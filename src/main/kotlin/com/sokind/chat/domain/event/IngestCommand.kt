package com.sokind.chat.domain.event

import java.time.Instant
import java.util.UUID

data class IngestCommand(
    val sessionId: UUID,
    val clientEventId: UUID,
    val userId: String,
    val type: EventType,
    val payload: Map<String, Any?>?,
    val clientTs: Instant,
)
