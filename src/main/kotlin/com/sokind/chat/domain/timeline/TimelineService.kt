package com.sokind.chat.domain.timeline

import com.sokind.chat.domain.event.DomainEvent
import com.sokind.chat.domain.event.EventRepository
import com.sokind.chat.domain.event.EventType
import com.sokind.chat.domain.participant.Presence
import com.sokind.chat.domain.session.SessionRepository
import com.sokind.chat.projection.snapshot.SnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

// 결정성 있는 상태 복원. 같은 (sessionId, at) → 항상 같은 Timeline.
// 1) 스냅샷이 있으면 그 지점부터 이후 이벤트만 fold (Snapshot+Delta)
// 2) 없으면 처음부터 전체 fold
// 두 경로 모두 같은 fold 함수를 사용.
@Service
class TimelineService(
    private val eventRepository: EventRepository,
    private val sessionRepository: SessionRepository,
    private val snapshotRepository: SnapshotRepository,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(TimelineService::class.java)

    @Transactional(readOnly = true)
    fun replay(sessionId: UUID, at: Instant): Timeline {
        if (!sessionRepository.existsById(sessionId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "session not found: $sessionId")
        }

        val presence = LinkedHashMap<String, Presence>()
        val messages = LinkedHashMap<Long, MessageView>()
        var upToSeq = 0L

        // at 시점까지 가장 큰 seq (없으면 null → 스냅샷 검색 생략)
        val boundarySeq = eventRepository.findMaxSeqUpTo(sessionId, at)

        // 그 지점 이하의 가장 최근 스냅샷
        val snapshot = boundarySeq?.let { boundary ->
            snapshotRepository.findTopBySessionIdAndUpToSeqLessThanEqualOrderByUpToSeqDesc(sessionId, boundary)
        }

        if (snapshot != null) {
            // 스냅샷 상태를 fold 초기값으로 사용
            val state = mapper.convertValue(snapshot.state, SnapshotState::class.java)
            state.participants.forEach { presence[it.userId] = it.presence }
            state.messages.forEach { messages[it.seq] = it }
            upToSeq = snapshot.upToSeq
            log.debug("replay using snapshot session={} upToSeq={}", sessionId, upToSeq)
        }

        // 스냅샷 이후의 차분 이벤트만 fold
        val events = if (upToSeq > 0L) {
            eventRepository.findSinceUpTo(sessionId, upToSeq, at)
        } else {
            eventRepository.findForReplay(sessionId, at)
        }

        for (event in events) {
            upToSeq = event.serverSeq ?: upToSeq
            when (event.type) {
                EventType.JOIN       -> presence[event.userId] = Presence.ONLINE
                EventType.LEAVE      -> presence.remove(event.userId)
                EventType.RECONNECT  -> if (presence.containsKey(event.userId)) presence[event.userId] = Presence.ONLINE
                EventType.DISCONNECT -> if (presence.containsKey(event.userId)) presence[event.userId] = Presence.OFFLINE
                EventType.MESSAGE    -> event.serverSeq?.let { seq ->
                    messages[seq] = MessageView(
                        seq = seq, userId = event.userId, clientTs = event.clientTs,
                        payload = event.payload, deleted = false, editedAtSeq = null,
                    )
                }
                EventType.EDIT   -> applyEdit(messages, event)
                EventType.DELETE -> applyDelete(messages, event)
            }
        }

        val participants = presence.entries.map { (uid, p) -> ParticipantView(uid, p) }
        return Timeline(at, upToSeq, participants, messages.values.toList())
    }

    private fun applyEdit(messages: MutableMap<Long, MessageView>, event: DomainEvent) {
        val target = readLong(event.payload, "targetSeq") ?: return
        val cur = messages[target] ?: return
        if (cur.deleted) return
        @Suppress("UNCHECKED_CAST")
        val next = (event.payload["newPayload"] as? Map<String, Any?>) ?: cur.payload
        messages[target] = cur.copy(payload = next, editedAtSeq = event.serverSeq)
    }

    private fun applyDelete(messages: MutableMap<Long, MessageView>, event: DomainEvent) {
        val target = readLong(event.payload, "targetSeq") ?: return
        val cur = messages[target] ?: return
        messages[target] = cur.copy(deleted = true)
    }

    private fun readLong(payload: Map<String, Any?>, key: String): Long? {
        val v = payload[key] ?: return null
        return when (v) {
            is Number -> v.toLong()
            else -> v.toString().toLongOrNull()
        }
    }
}
