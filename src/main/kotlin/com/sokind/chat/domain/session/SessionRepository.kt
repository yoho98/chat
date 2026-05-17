package com.sokind.chat.domain.session

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SessionRepository : JpaRepository<Session, UUID> {

    @Query("""
        SELECT session FROM Session session
         WHERE (:status IS NULL OR session.status = :status)
           AND (:from   IS NULL OR session.createdAt >= :from)
           AND (:to     IS NULL OR session.createdAt <= :to)
           AND (:userId IS NULL OR EXISTS (
                 SELECT 1 FROM Participant p
                  WHERE p.sessionId = session.id
                    AND p.userId    = :userId))
         ORDER BY session.createdAt DESC
    """)
    fun search(
        @Param("status") status: SessionStatus?,
        @Param("from") from: Instant?,
        @Param("to") to: Instant?,
        @Param("userId") userId: String?,
    ): List<Session>
}
