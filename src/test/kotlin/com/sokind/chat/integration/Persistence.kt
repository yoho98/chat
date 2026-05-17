package com.sokind.chat.integration

import com.sokind.chat.domain.event.EventIngestService
import com.sokind.chat.domain.event.EventRepository
import com.sokind.chat.domain.event.EventType
import com.sokind.chat.domain.event.IngestCommand
import com.sokind.chat.domain.participant.ParticipantId
import com.sokind.chat.domain.participant.ParticipantRepository
import com.sokind.chat.domain.participant.Presence
import com.sokind.chat.domain.session.Session
import com.sokind.chat.domain.session.SessionRepository
import com.sokind.chat.domain.session.SessionStatus
import com.sokind.chat.domain.timeline.TimelineService
import com.sokind.chat.projection.outbox.OutboxRepository
import com.sokind.chat.projection.outbox.OutboxStatus
import com.sokind.chat.projection.snapshot.SnapshotRepository
import com.sokind.chat.projection.worker.ProjectionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

// DB 통합 테스트. MySQL 8.0 컨테이너로 실행. 멱등 ingest, outbox/워커, 스냅샷, fold 검증.
@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = [
    "snapshot.every-n=5",
    // 백그라운드 워커가 끼어들지 않도록 사실상 비활성화 — 테스트가 직접 processBatch 호출
    "projection.tick-ms=9999999999",
    // 즉시 리스너도 비활성화해 결과를 결정적으로 만든다 (운영 기본값은 true)
    "projection.immediate=false",
])
class Persistence {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("sokind_chat")
            .withUsername("sokind")
            .withPassword("sokindpw")
            .withCommand("--character-set-server=utf8mb4", "--default-time-zone=+00:00", "--innodb-autoinc-lock-mode=2")
    }

    @Autowired lateinit var sessionRepository: SessionRepository
    @Autowired lateinit var eventRepository: EventRepository
    @Autowired lateinit var participantRepository: ParticipantRepository
    @Autowired lateinit var outboxRepository: OutboxRepository
    @Autowired lateinit var snapshotRepository: SnapshotRepository
    @Autowired lateinit var ingest: EventIngestService
    @Autowired lateinit var projection: ProjectionService
    @Autowired lateinit var timeline: TimelineService
    @Autowired lateinit var tx: TransactionTemplate

    private fun fresh(): Session = sessionRepository.saveAndFlush(Session.create())

    private fun cleanup() = tx.execute {
        snapshotRepository.deleteAllInBatch()
        outboxRepository.deleteAllInBatch()
        participantRepository.deleteAllInBatch()
        eventRepository.deleteAllInBatch()
        sessionRepository.deleteAllInBatch()
    }

    @Test
    fun `같은_clientEventId로_재전송하면_단일_row와_동일한_serverSeq를_반환한다`() {
        cleanup()
        val session = fresh()
        val k = UUID.randomUUID()

        val firstResult = ingest.ingest(IngestCommand(session.id, k, "alice", EventType.MESSAGE, mapOf("text" to "first"), Instant.now()))
        val secondResult = ingest.ingest(IngestCommand(session.id, k, "alice", EventType.MESSAGE, mapOf("text" to "would-be-ignored"), Instant.now()))

        assertFalse(firstResult.duplicate)
        assertTrue(secondResult.duplicate)
        assertEquals(firstResult.serverSeq, secondResult.serverSeq)

        assertEquals(1, eventRepository.findForReplay(session.id, Instant.parse("2099-01-01T00:00:00Z")).size)
        assertEquals(1, outboxRepository.findAll().count { it.eventSeq == firstResult.serverSeq })
    }

    @Test
    fun `events_INSERT와_outbox_INSERT는_같은_트랜잭션에서_커밋된다`() {
        cleanup()
        val session = fresh()
        val result = ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "alice",
            EventType.MESSAGE, mapOf("text" to "atomic"), Instant.now()))

        val outboxRow = outboxRepository.findAll().firstOrNull { it.eventSeq == result.serverSeq }
        assertNotNull(outboxRow)
        assertEquals(OutboxStatus.PENDING, outboxRow!!.status)
    }

    @Test
    fun `프로젝션_워커는_PENDING_outbox를_비우고_participants_캐시를_갱신한다`() {
        cleanup()
        val session = fresh()
        ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "alice", EventType.JOIN, emptyMap(), Instant.now()))
        ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "bob",   EventType.JOIN, emptyMap(), Instant.now()))

        projection.processBatch()

        val cached = participantRepository.findBySessionId(session.id).associateBy { it.userId }
        assertEquals(setOf("alice", "bob"), cached.keys)
        assertTrue(cached.values.all { it.presence == Presence.ONLINE })

        val pending = outboxRepository.findAll().count { it.status == OutboxStatus.PENDING }
        assertEquals(0, pending)
    }

    @Test
    fun `DISCONNECT_이벤트는_presence만_OFFLINE으로_바꾸고_참여자를_삭제하지_않는다`() {
        cleanup()
        val session = fresh()
        ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "alice", EventType.JOIN, emptyMap(), Instant.now()))
        projection.processBatch()
        ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "alice", EventType.DISCONNECT, emptyMap(), Instant.now()))
        projection.processBatch()

        val participant = participantRepository.findById(ParticipantId(session.id, "alice")).get()
        assertEquals(Presence.OFFLINE, participant.presence)
    }

    @Test
    fun `매_N개_이벤트마다_스냅샷이_생성된다`() {
        cleanup()
        val session = fresh()
        ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "alice", EventType.JOIN, emptyMap(), Instant.now()))
        ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "bob",   EventType.JOIN, emptyMap(), Instant.now()))
        repeat(8) { i ->
            ingest.ingest(IngestCommand(
                session.id, UUID.randomUUID(), if (i % 2 == 0) "alice" else "bob",
                EventType.MESSAGE, mapOf("text" to "messageEntry$i"), Instant.now()))
        }
        projection.processBatch()

        // 스냅샷은 commit 후 비동기 저장이라 processBatch 직후엔 아직 없을 수 있음 → 폴링 대기
        val deadline = System.currentTimeMillis() + 3_000L
        var snaps = snapshotRepository.findAll().filter { it.sessionId == session.id }
        while (snaps.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            snaps = snapshotRepository.findAll().filter { it.sessionId == session.id }
        }
        // N=5 단위 경계에서 스냅샷이 1개 이상 만들어졌는지 확인
        assertTrue(snaps.isNotEmpty(), "N개 단위 경계에서 스냅샷이 최소 1개는 만들어져야 한다")
        @Suppress("UNCHECKED_CAST")
        val firstState = snaps.first().state
        // 스냅샷 안에 fold 결과(participants/messages) 가 들어있어야 함
        assertTrue((firstState["participants"] as List<*>).isNotEmpty(), "snapshot.participants 비어있지 않음")
    }

    @Test
    fun `timeline_fold는_events로부터_참여자와_메시지를_재구성한다`() {
        cleanup()
        val session = fresh()
        ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "alice", EventType.JOIN, emptyMap(), Instant.now()))
        ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "bob",   EventType.JOIN, emptyMap(), Instant.now()))
        val m1 = ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "alice", EventType.MESSAGE, mapOf("text" to "high"), Instant.now()))
        ingest.ingest(IngestCommand(session.id, UUID.randomUUID(), "alice",
            EventType.EDIT, mapOf("targetSeq" to m1.serverSeq, "newPayload" to mapOf("text" to "high!")), Instant.now()))

        val timeline = timeline.replay(session.id, Instant.parse("2099-01-01T00:00:00Z"))
        assertEquals(2, timeline.participants.size)
        assertEquals(1, timeline.messages.size)
        assertEquals("high!", timeline.messages[0].payload["text"])
    }

    @Test
    fun `세션_목록은_status로_필터링된다`() {
        cleanup()
        val a = sessionRepository.saveAndFlush(Session.create())
        val b = sessionRepository.saveAndFlush(Session.create())
        b.end()
        sessionRepository.saveAndFlush(b)

        val active = sessionRepository.search(SessionStatus.ACTIVE, null, null, null)
        val ended  = sessionRepository.search(SessionStatus.ENDED,  null, null, null)
        assertEquals(setOf(a.id), active.map { it.id }.toSet())
        assertEquals(setOf(b.id), ended.map { it.id }.toSet())
    }
}
