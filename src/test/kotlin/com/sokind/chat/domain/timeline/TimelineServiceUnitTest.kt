package com.sokind.chat.domain.timeline

import com.sokind.chat.domain.event.DomainEvent
import com.sokind.chat.domain.event.EventRepository
import com.sokind.chat.domain.event.EventType
import com.sokind.chat.domain.participant.Presence
import com.sokind.chat.domain.session.SessionRepository
import com.sokind.chat.projection.snapshot.SnapshotRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

// fold 로직 단위 테스트. Spring/DB 없음. 스냅샷은 null 로 두고 전체 fold 경로만 검증.
class TimelineServiceUnitTest {

    private val eventRepository = mockk<EventRepository>()
    private val sessionRepository = mockk<SessionRepository>()
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val mapper = mockk<ObjectMapper>(relaxed = true)
    private val service = TimelineService(eventRepository, sessionRepository, snapshotRepository, mapper)

    private val sid: UUID = UUID.randomUUID()
    private val far: Instant = Instant.parse("2099-01-01T00:00:00Z")

    private fun stub(events: List<DomainEvent>) {
        every { sessionRepository.existsById(sid) } returns true
        every { eventRepository.findForReplay(sid, any()) } returns events
        every { eventRepository.findMaxSeqUpTo(sid, any()) } returns events.lastOrNull()?.serverSeq
        every {
            snapshotRepository.findTopBySessionIdAndUpToSeqLessThanEqualOrderByUpToSeqDesc(sid, any())
        } returns null
    }

    private fun ev(
        seq: Long, type: EventType, user: String = "alice",
        payload: Map<String, Any?> = emptyMap(),
        clientTs: Instant = Instant.parse("2026-05-15T00:00:00Z"),
    ): DomainEvent = DomainEvent(
        serverSeq = seq, sessionId = sid, clientEventId = UUID.randomUUID(),
        userId = user, type = type, payload = payload,
        clientTs = clientTs, serverTs = clientTs,
    )

    @Test
    fun `참여_후_퇴장하면_참여자_목록에서_제거된다`() {
        stub(listOf(
            ev(1, EventType.JOIN, "alice"),
            ev(2, EventType.JOIN, "bob"),
            ev(3, EventType.LEAVE, "alice"),
        ))
        val timeline = service.replay(sid, far)
        assertEquals(1, timeline.participants.size)
        assertEquals("bob", timeline.participants[0].userId)
        assertEquals(Presence.ONLINE, timeline.participants[0].presence)
    }

    @Test
    fun `메시지_이후_수정_이벤트는_페이로드를_갱신하고_editedAtSeq를_기록한다`() {
        stub(listOf(
            ev(1, EventType.JOIN, "alice"),
            ev(2, EventType.MESSAGE, "alice", mapOf("text" to "high")),
            ev(3, EventType.EDIT, "alice", mapOf("targetSeq" to 2, "newPayload" to mapOf("text" to "high!"))),
        ))
        val timeline = service.replay(sid, far)
        assertEquals(1, timeline.messages.size)
        assertEquals("high!", timeline.messages[0].payload["text"])
        assertEquals(3L, timeline.messages[0].editedAtSeq)
        assertEquals(false, timeline.messages[0].deleted)
    }

    @Test
    fun `메시지_이후_삭제_이벤트는_deleted_플래그만_세우고_페이로드는_유지한다`() {
        stub(listOf(
            ev(1, EventType.JOIN, "alice"),
            ev(2, EventType.MESSAGE, "alice", mapOf("text" to "oops")),
            ev(3, EventType.DELETE, "alice", mapOf("targetSeq" to 2)),
        ))
        val timeline = service.replay(sid, far)
        assertEquals(1, timeline.messages.size)
        assertTrue(timeline.messages[0].deleted)
        assertEquals("oops", timeline.messages[0].payload["text"])
        assertNull(timeline.messages[0].editedAtSeq)
    }

    @Test
    fun `재접속_이벤트는_기존_사용자를_ONLINE_상태로_되돌린다`() {
        stub(listOf(
            ev(1, EventType.JOIN, "alice"),
            ev(2, EventType.DISCONNECT, "alice"),
            ev(3, EventType.RECONNECT, "alice"),
        ))
        val timeline = service.replay(sid, far)
        assertEquals(Presence.ONLINE, timeline.participants.single().presence)
    }

    @Test
    fun `재접속_이벤트는_참여_이력이_없는_사용자에게는_무시된다`() {
        stub(listOf(
            ev(1, EventType.RECONNECT, "ghost"),
        ))
        val timeline = service.replay(sid, far)
        assertTrue(timeline.participants.isEmpty())
    }

    @Test
    fun `정렬은_server_seq_기준이며_client_ts는_무시된다`() {
        stub(listOf(
            ev(1, EventType.JOIN, "alice"),
            ev(2, EventType.MESSAGE, "alice", mapOf("text" to "seq가_먼저"),
                clientTs = Instant.parse("2026-05-15T10:00:00Z")),
            ev(3, EventType.MESSAGE, "alice", mapOf("text" to "seq가_나중"),
                clientTs = Instant.parse("2026-05-15T09:00:00Z")),
        ))
        val timeline = service.replay(sid, far)
        assertEquals(listOf("seq가_먼저", "seq가_나중"),
            timeline.messages.map { it.payload["text"] })
    }

    @Test
    fun `같은_입력에_대해_fold_결과는_항상_동일하다`() {
        val events = listOf(
            ev(1, EventType.JOIN, "alice"),
            ev(2, EventType.MESSAGE, "alice", mapOf("text" to "x")),
            ev(3, EventType.JOIN, "bob"),
            ev(4, EventType.MESSAGE, "bob", mapOf("text" to "y")),
            ev(5, EventType.EDIT, "alice", mapOf("targetSeq" to 2, "newPayload" to mapOf("text" to "x2"))),
        )
        stub(events)
        val a = service.replay(sid, far)
        val b = service.replay(sid, far)
        assertEquals(a, b)
    }

    @Test
    fun `upToSeq는_마지막으로_fold한_이벤트의_server_seq를_담는다`() {
        stub(listOf(
            ev(7, EventType.JOIN, "alice"),
            ev(11, EventType.MESSAGE, "alice"),
            ev(42, EventType.LEAVE, "alice"),
        ))
        val timeline = service.replay(sid, far)
        assertEquals(42L, timeline.upToSeq)
    }

    @Test
    fun `target이_없는_수정_이벤트는_조용히_무시된다`() {
        stub(listOf(
            ev(1, EventType.JOIN, "alice"),
            ev(2, EventType.EDIT, "alice", mapOf("targetSeq" to 999, "newPayload" to mapOf("text" to "ghost"))),
        ))
        val timeline = service.replay(sid, far)
        assertTrue(timeline.messages.isEmpty())
    }

    @Test
    fun `이미_삭제된_메시지에_대한_수정_이벤트는_되살리지_않는다`() {
        stub(listOf(
            ev(1, EventType.JOIN, "alice"),
            ev(2, EventType.MESSAGE, "alice", mapOf("text" to "x")),
            ev(3, EventType.DELETE, "alice", mapOf("targetSeq" to 2)),
            ev(4, EventType.EDIT, "alice", mapOf("targetSeq" to 2, "newPayload" to mapOf("text" to "x2"))),
        ))
        val timeline = service.replay(sid, far)
        assertEquals(1, timeline.messages.size)
        val messageEntry = timeline.messages.single()
        assertTrue(messageEntry.deleted)
        assertEquals("x", messageEntry.payload["text"])
    }
}
