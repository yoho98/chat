package com.sokind.chat.projection.outbox

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.time.Instant

interface OutboxRepository : JpaRepository<OutboxEntry, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
        SELECT outboxEntry FROM OutboxEntry outboxEntry
         WHERE outboxEntry.status = com.sokind.chat.projection.outbox.OutboxStatus.PENDING
           AND outboxEntry.nextAttempt <= :now
         ORDER BY outboxEntry.id ASC
    """)
    fun pollPending(@Param("now") now: Instant, pageable: Pageable): List<OutboxEntry>

    // 상태별 row 수 (메트릭용)
    fun countByStatus(status: OutboxStatus): Long

    // 가장 오래된 미처리 row 의 생성 시각 (지연 측정용)
    @Query("""
        SELECT MIN(outboxEntry.createdAt) FROM OutboxEntry outboxEntry
         WHERE outboxEntry.status = com.sokind.chat.projection.outbox.OutboxStatus.PENDING
    """)
    fun oldestPendingCreatedAt(): Instant?
}
