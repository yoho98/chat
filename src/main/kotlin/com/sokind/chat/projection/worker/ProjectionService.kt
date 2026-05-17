package com.sokind.chat.projection.worker

import com.sokind.chat.domain.event.DomainEvent
import com.sokind.chat.domain.event.EventRepository
import com.sokind.chat.domain.event.EventType
import com.sokind.chat.domain.participant.Participant
import com.sokind.chat.domain.participant.ParticipantId
import com.sokind.chat.domain.participant.ParticipantRepository
import com.sokind.chat.projection.outbox.OutboxRepository
import com.sokind.chat.projection.snapshot.SnapshotRequested
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.math.pow

@Service
class ProjectionService(
    private val outboxRepository: OutboxRepository,
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(ProjectionService::class.java)

    private val maxRetry = 5
    private val batchSize = 50

    @Transactional
    fun processBatch() {
        val rows = outboxRepository.pollPending(Instant.now(), PageRequest.of(0, batchSize))
        if (rows.isEmpty()) return
        log.debug("projection batch size={}", rows.size)

        for (outboxEntry in rows) {
            try {
                val event = eventRepository.findById(outboxEntry.eventSeq).orElse(null)
                if (event == null) {
                    outboxEntry.markDone()
                    continue
                }
                applyToReadModel(event)
                outboxEntry.markDone()
                // 스냅샷은 commit 후 비동기로 처리 → 워커 루프를 막지 않음
                event.serverSeq?.let { eventPublisher.publishEvent(SnapshotRequested(event.sessionId, it)) }
            } catch (exception: Exception) {
                log.warn("projection failure eventSeq={} retry={} reason={}", outboxEntry.eventSeq, outboxEntry.retryCount, exception.message)
                val backoffSec = 2.0.pow(outboxEntry.retryCount.toDouble()).toLong().coerceAtLeast(1L)
                outboxEntry.markFailure(exception.message ?: "unknown", Instant.now().plusSeconds(backoffSec), maxRetry)
            }
        }
    }

    private fun applyToReadModel(event: DomainEvent) {
        val participantId = ParticipantId(event.sessionId, event.userId)
        when (event.type) {
            EventType.JOIN -> {
                val exception = participantRepository.findById(participantId)
                if (exception.isPresent) {
                    exception.get().also { it.markOnline(); it.leftAt = null }
                } else {
                    participantRepository.save(Participant.join(event.sessionId, event.userId))
                }
            }
            EventType.LEAVE      -> participantRepository.findById(participantId).ifPresent { it.leave() }
            EventType.RECONNECT  -> {
                val exception = participantRepository.findById(participantId)
                if (exception.isPresent) exception.get().markOnline()
                else participantRepository.save(Participant.join(event.sessionId, event.userId))
            }
            EventType.DISCONNECT -> participantRepository.findById(participantId).ifPresent { it.markOffline() }
            else -> Unit
        }
    }
}
