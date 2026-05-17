package com.sokind.chat.api.session

import com.sokind.chat.domain.session.Session
import com.sokind.chat.domain.session.SessionStatus
import java.time.Instant
import java.util.UUID

data class SessionResponse(
    val id: UUID,
    val status: SessionStatus,
    val createdAt: Instant,
    val endedAt: Instant?,
) {
    companion object {
        fun of(session: Session) = SessionResponse(session.id, session.status, session.createdAt, session.endedAt)
    }
}
