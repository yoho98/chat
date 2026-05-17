package com.sokind.chat.api.event

import com.sokind.chat.domain.event.EventType
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class IngestRequest(
    @field:NotNull val clientEventId: UUID?,
    @field:NotNull val type: EventType?,
    val payload: Map<String, Any?>? = null,
    @field:NotNull val clientTs: Instant?,
)
