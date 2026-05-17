package com.sokind.chat.domain.session

import com.sokind.chat.domain.event.EventIngestService
import com.sokind.chat.domain.event.EventType
import com.sokind.chat.domain.event.IngestCommand
import com.sokind.chat.domain.event.IngestResult
import com.sokind.chat.domain.participant.Participant
import com.sokind.chat.domain.participant.ParticipantRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class SessionService(
    private val sessionRepository: SessionRepository,
    private val ingestService: EventIngestService,
    private val participantRepository: ParticipantRepository,
) {

    @Transactional
    fun create(): Session = sessionRepository.save(Session.create())

    @Transactional
    fun join(sessionId: UUID, userId: String): IngestResult {
        val session = mustFind(sessionId)
        if (session.status == SessionStatus.ENDED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "session already ended")
        }
        return ingestService.ingest(IngestCommand(
            sessionId = sessionId,
            clientEventId = UUID.randomUUID(),
            userId = userId,
            type = EventType.JOIN,
            payload = emptyMap(),
            clientTs = Instant.now(),
        ))
    }

    @Transactional
    fun leave(sessionId: UUID, userId: String): IngestResult {
        mustFind(sessionId)
        return ingestService.ingest(IngestCommand(
            sessionId = sessionId,
            clientEventId = UUID.randomUUID(),
            userId = userId,
            type = EventType.LEAVE,
            payload = emptyMap(),
            clientTs = Instant.now(),
        ))
    }

    @Transactional
    fun end(sessionId: UUID): Session {
        val session = mustFind(sessionId)
        session.end()
        return session
    }

    @Transactional(readOnly = true)
    fun get(sessionId: UUID): Session = mustFind(sessionId)

    @Transactional(readOnly = true)
    fun list(status: SessionStatus?, from: Instant?, to: Instant?, userId: String?): List<Session> =
        sessionRepository.search(status, from, to, userId)

    // 캐시된 참여자 목록. fold 없이 빠른 조회. 정상 경로 ~0ms, 폴링만이면 최대 5s 지연.
    @Transactional(readOnly = true)
    fun participantsOf(sessionId: UUID): List<Participant> {
        mustFind(sessionId)
        return participantRepository.findBySessionId(sessionId)
    }

    private fun mustFind(sessionId: UUID): Session =
        sessionRepository.findById(sessionId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "session not found: $sessionId")
        }
}
