package com.sokind.chat.domain.event

import com.sokind.chat.infrastructure.metrics.ProjectionMetrics
import com.sokind.chat.projection.outbox.OutboxAppended
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.Statement
import java.sql.Timestamp

// 이벤트 적재 (append-only). DB UNIQUE(session_id, client_event_id) 로 멱등성 보장.
// events INSERT 와 outbox INSERT 를 같은 트랜잭션에 묶어 누락을 방지.
@Service
class EventIngestService(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val metrics: ProjectionMetrics,
    private val publisher: ApplicationEventPublisher,
) {

    @Transactional
    fun ingest(command: IngestCommand): IngestResult {
        val sessionIdStr = command.sessionId.toString()
        val clientEventIdStr = command.clientEventId.toString()
        val payloadJson = mapper.writeValueAsString(command.payload ?: emptyMap<String, Any?>())

        val keyHolder = GeneratedKeyHolder()
        val rows = jdbc.update({ con ->
            val ps = con.prepareStatement(
                """
                INSERT IGNORE INTO events
                    (session_id, client_event_id, user_id, type, payload, client_ts)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                Statement.RETURN_GENERATED_KEYS,
            )
            ps.setString(1, sessionIdStr)
            ps.setString(2, clientEventIdStr)
            ps.setString(3, command.userId)
            ps.setString(4, command.type.name)
            ps.setString(5, payloadJson)
            ps.setTimestamp(6, Timestamp.from(command.clientTs))
            ps
        }, keyHolder)

        val generated = keyHolder.key?.toLong() ?: 0L
        if (rows == 1 && generated > 0L) {
            // outbox PK 확보 — commit 후 리스너가 이 id 로 row 식별
            val outboxKey = GeneratedKeyHolder()
            jdbc.update({ con ->
                val ps = con.prepareStatement(
                    "INSERT INTO projection_outbox (event_seq) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS,
                )
                ps.setLong(1, generated)
                ps
            }, outboxKey)
            val outboxId = outboxKey.key?.toLong() ?: 0L
            // 트랜잭션 안에서 publish → Spring 이 commit 이후 리스너 호출
            publisher.publishEvent(OutboxAppended(outboxId, generated, command.sessionId))
            metrics.recordIngest(command.type, duplicate = false)
            return IngestResult(generated, duplicate = false)
        }

        val existing = jdbc.queryForObject(
            "SELECT server_seq FROM events WHERE session_id = ? AND client_event_id = ?",
            Long::class.java,
            sessionIdStr, clientEventIdStr,
        ) ?: error("INSERT IGNORE returned no rows but duplicate row not found — schema constraint violation?")
        // 중복이면 outbox 도 안 만들고 publish 도 안 함
        metrics.recordIngest(command.type, duplicate = true)
        return IngestResult(existing, duplicate = true)
    }
}
