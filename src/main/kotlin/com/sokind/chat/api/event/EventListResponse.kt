package com.sokind.chat.api.event

import com.sokind.chat.domain.event.DomainEvent
import com.sokind.chat.domain.event.EventType
import java.time.Instant
import java.util.UUID

// 이벤트 목록 응답. nextFromSeq 가 null 이 아니면 다음 페이지 있음.
data class EventListResponse(
    val items: List<EventItem>,
    val nextFromSeq: Long?,
)

data class EventItem(
    val serverSeq: Long,
    val sessionId: UUID,
    val type: EventType,
    val userId: String,
    val payload: Map<String, Any?>,
    val clientTs: Instant,
    val serverTs: Instant,
) {
    companion object {
        fun of(event: DomainEvent) = EventItem(
            serverSeq = event.serverSeq ?: 0L,
            sessionId = event.sessionId,
            type      = event.type,
            userId    = event.userId,
            payload   = event.payload,
            clientTs  = event.clientTs,
            serverTs  = event.serverTs,
        )
    }
}
