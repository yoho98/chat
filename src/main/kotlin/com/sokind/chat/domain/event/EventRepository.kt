package com.sokind.chat.domain.event

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface EventRepository : JpaRepository<DomainEvent, Long> {

    fun findBySessionIdAndClientEventId(sessionId: UUID, clientEventId: UUID): DomainEvent?

    @Query("""
        SELECT event FROM DomainEvent event
         WHERE event.sessionId = :sessionId
           AND event.serverTs <= :at
         ORDER BY event.serverSeq ASC
    """)
    fun findForReplay(
        @Param("sessionId") sessionId: UUID,
        @Param("at") at: Instant,
    ): List<DomainEvent>

    @Query("""
        SELECT event FROM DomainEvent event
         WHERE event.sessionId = :sessionId
           AND event.serverSeq > :since
         ORDER BY event.serverSeq ASC
    """)
    fun findSince(
        @Param("sessionId") sessionId: UUID,
        @Param("since") since: Long,
    ): List<DomainEvent>

    // 스냅샷 이후의 차분 구간 조회 (snapshot.upToSeq < seq <= at).
    @Query("""
        SELECT event FROM DomainEvent event
         WHERE event.sessionId = :sessionId
           AND event.serverSeq > :since
           AND event.serverTs <= :at
         ORDER BY event.serverSeq ASC
    """)
    fun findSinceUpTo(
        @Param("sessionId") sessionId: UUID,
        @Param("since") since: Long,
        @Param("at") at: Instant,
    ): List<DomainEvent>

    // at 시점까지 가장 큰 server_seq. 이벤트가 없으면 null.
    @Query("""
        SELECT MAX(event.serverSeq) FROM DomainEvent event
         WHERE event.sessionId = :sessionId
           AND event.serverTs <= :at
    """)
    fun findMaxSeqUpTo(
        @Param("sessionId") sessionId: UUID,
        @Param("at") at: Instant,
    ): Long?

    // 범위 조회. fromSeq 는 제외(>), toSeq 는 포함(<=). 페이지 크기는 호출부에서 제한.
    @Query("""
        SELECT event FROM DomainEvent event
         WHERE event.sessionId = :sessionId
           AND (:fromSeq IS NULL OR event.serverSeq >  :fromSeq)
           AND (:toSeq   IS NULL OR event.serverSeq <= :toSeq)
           AND (:from    IS NULL OR event.serverTs  >= :from)
           AND (:to      IS NULL OR event.serverTs  <= :to)
         ORDER BY event.serverSeq ASC
    """)
    fun findRange(
        @Param("sessionId") sessionId: UUID,
        @Param("fromSeq") fromSeq: Long?,
        @Param("toSeq") toSeq: Long?,
        @Param("from") from: Instant?,
        @Param("to") to: Instant?,
        pageable: Pageable,
    ): List<DomainEvent>
}
