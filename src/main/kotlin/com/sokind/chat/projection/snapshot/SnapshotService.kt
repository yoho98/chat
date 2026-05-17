package com.sokind.chat.projection.snapshot

import com.sokind.chat.domain.timeline.TimelineService
import com.sokind.chat.infrastructure.metrics.ProjectionMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SnapshotService(
    private val timelineService: TimelineService,
    private val snapshotRepository: SnapshotRepository,
    private val metrics: ProjectionMetrics,
    @Value("\${snapshot.every-n:50}") private val every: Long,
) {
    private val log = LoggerFactory.getLogger(SnapshotService::class.java)

    fun maybeSnapshot(sessionId: UUID, serverSeq: Long) {
        if (every <= 0 || serverSeq <= 0 || serverSeq % every != 0L) return
        writeSnapshotIfMissing(sessionId, Instant.now())
    }

    // 강제 스냅샷. 같은 지점에 이미 있으면 created=false 로 멱등 응답.
    @Transactional
    fun forceSnapshot(sessionId: UUID): SnapshotResult =
        writeSnapshotIfMissing(sessionId, Instant.now())
            ?: SnapshotResult(sessionId = sessionId, upToSeq = 0L, created = false, createdAt = Instant.now())

    private fun writeSnapshotIfMissing(sessionId: UUID, at: Instant): SnapshotResult? {
        val timeline = timelineService.replay(sessionId, at)
        if (timeline.upToSeq <= 0) return null

        // 같은 (sessionId, upToSeq) 가 이미 있으면 스킵 — PK 충돌로 트랜잭션 롤백되는 것 방지
        val id = SnapshotId(sessionId, timeline.upToSeq)
        if (snapshotRepository.existsById(id)) {
            return SnapshotResult(sessionId = sessionId, upToSeq = timeline.upToSeq, created = false, createdAt = Instant.now())
        }

        val state: Map<String, Any?> = mapOf(
            "participants" to timeline.participants,
            "messages"     to timeline.messages,
            "upToSeq"      to timeline.upToSeq,
        )
        // saveAndFlush 로 바로 DB 반영 → 같은 트랜잭션 내 다음 호출이 existsById 로 중복 감지
        val saved = snapshotRepository.saveAndFlush(Snapshot(sessionId = sessionId, upToSeq = timeline.upToSeq, state = state))
        metrics.recordSnapshot()
        log.info("snapshot saved session={} upToSeq={}", sessionId, timeline.upToSeq)
        return SnapshotResult(sessionId = sessionId, upToSeq = saved.upToSeq, created = true, createdAt = saved.createdAt)
    }
}

data class SnapshotResult(
    val sessionId: UUID,
    val upToSeq: Long,
    val created: Boolean,
    val createdAt: Instant,
)
