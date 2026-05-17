package com.sokind.chat.domain.participant

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "participants")
@IdClass(ParticipantId::class)
class Participant(

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "session_id", length = 36, columnDefinition = "VARCHAR(36) CHARACTER SET ascii")
    val sessionId: UUID = UUID(0L, 0L),

    @Id
    @Column(name = "user_id", length = 64)
    val userId: String = "",

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),

    @Column(name = "left_at")
    var leftAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "presence", length = 16, nullable = false)
    var presence: Presence = Presence.OFFLINE,
) {
    fun leave() {
        leftAt = Instant.now()
        presence = Presence.OFFLINE
    }

    fun markOnline()  { presence = Presence.ONLINE }
    fun markOffline() { presence = Presence.OFFLINE }

    companion object {
        fun join(sessionId: UUID, userId: String) = Participant(
            sessionId = sessionId,
            userId = userId,
            joinedAt = Instant.now(),
            presence = Presence.ONLINE,
        )
    }
}
