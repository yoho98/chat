package com.sokind.chat.domain.event

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "events")
class DomainEvent(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "server_seq")
    val serverSeq: Long? = null,

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "session_id", length = 36, columnDefinition = "VARCHAR(36) CHARACTER SET ascii", nullable = false, updatable = false)
    val sessionId: UUID = UUID(0L, 0L),

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "client_event_id", length = 36, columnDefinition = "VARCHAR(36) CHARACTER SET ascii", nullable = false, updatable = false)
    val clientEventId: UUID = UUID(0L, 0L),

    @Column(name = "user_id", length = 64, nullable = false, updatable = false)
    val userId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 24, nullable = false, updatable = false)
    val type: EventType = EventType.MESSAGE,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "JSON")
    val payload: Map<String, Any?> = emptyMap(),

    @Column(name = "client_ts", nullable = false, updatable = false)
    val clientTs: Instant = Instant.EPOCH,

    @Column(name = "server_ts", nullable = false, updatable = false)
    val serverTs: Instant = Instant.EPOCH,
)
