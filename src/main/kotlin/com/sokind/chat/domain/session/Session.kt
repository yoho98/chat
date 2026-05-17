package com.sokind.chat.domain.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sessions")
class Session(

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, columnDefinition = "VARCHAR(36) CHARACTER SET ascii")
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    var status: SessionStatus = SessionStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "ended_at")
    var endedAt: Instant? = null,
) {
    fun end() {
        if (status == SessionStatus.ENDED) return
        status = SessionStatus.ENDED
        endedAt = Instant.now()
    }

    companion object {
        fun create(): Session = Session()
    }
}
