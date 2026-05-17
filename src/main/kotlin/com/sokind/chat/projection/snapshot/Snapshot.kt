package com.sokind.chat.projection.snapshot

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "snapshots")
@IdClass(SnapshotId::class)
class Snapshot(

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "session_id", length = 36, columnDefinition = "VARCHAR(36) CHARACTER SET ascii")
    val sessionId: UUID = UUID(0L, 0L),

    @Id
    @Column(name = "up_to_seq")
    val upToSeq: Long = 0L,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state", nullable = false, columnDefinition = "JSON")
    val state: Map<String, Any?> = emptyMap(),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
