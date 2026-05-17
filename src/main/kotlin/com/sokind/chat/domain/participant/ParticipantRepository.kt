package com.sokind.chat.domain.participant

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ParticipantRepository : JpaRepository<Participant, ParticipantId> {
    fun findBySessionId(sessionId: UUID): List<Participant>
}
