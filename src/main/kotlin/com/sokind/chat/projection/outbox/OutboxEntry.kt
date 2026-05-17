package com.sokind.chat.projection.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "projection_outbox")
class OutboxEntry(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "event_seq", nullable = false)
    val eventSeq: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "next_attempt", nullable = false)
    var nextAttempt: Instant = Instant.now(),

    @Column(name = "last_error")
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun markInProgress() {
        status = OutboxStatus.IN_PROGRESS
        updatedAt = Instant.now()
    }

    fun markDone() {
        status = OutboxStatus.DONE
        updatedAt = Instant.now()
    }

    fun markFailure(err: String, next: Instant, maxRetry: Int) {
        retryCount += 1
        lastError = err
        nextAttempt = next
        updatedAt = Instant.now()
        status = if (retryCount > maxRetry) OutboxStatus.DEAD else OutboxStatus.PENDING
    }
}
