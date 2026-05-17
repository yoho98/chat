package com.sokind.chat.infrastructure.metrics

import com.sokind.chat.domain.event.EventType
import com.sokind.chat.projection.outbox.OutboxRepository
import com.sokind.chat.projection.outbox.OutboxStatus
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// Prometheus 메트릭. /actuator/prometheus 로 노출.
// - sokind.outbox.pending / dead : 처리 대기/실패 큐 크기
// - sokind.projection.lag        : 가장 오래된 미처리 이벤트 지연(ms)
// - sokind.snapshot.total        : 누적 스냅샷 수
// - sokind.events.ingested       : 누적 이벤트 수 (type/중복 태그)
@Component
class ProjectionMetrics(
    private val outboxRepository: OutboxRepository,
    private val registry: MeterRegistry,
) {

    private val ingestCounters = ConcurrentHashMap<Pair<EventType, Boolean>, Counter>()
    private lateinit var snapshotCounter: Counter

    @PostConstruct
    fun register() {
        registry.gauge("sokind.outbox.pending", this) { it.safeCount(OutboxStatus.PENDING).toDouble() }
        registry.gauge("sokind.outbox.dead",    this) { it.safeCount(OutboxStatus.DEAD).toDouble()    }
        registry.gauge("sokind.projection.lag", this) { it.safeLagMs().toDouble() }

        snapshotCounter = Counter.builder("sokind.snapshot.total")
            .description("누적 생성된 snapshot row 수")
            .register(registry)
    }

    fun recordIngest(type: EventType, duplicate: Boolean) {
        ingestCounters.computeIfAbsent(type to duplicate) { (eventType, isDup) ->
            Counter.builder("sokind.events.ingested")
                .description("누적 인제스트 이벤트 수")
                .tag("type", eventType.name)
                .tag("duplicate", isDup.toString())
                .register(registry)
        }.increment()
    }

    fun recordSnapshot() {
        snapshotCounter.increment()
    }

    // gauge 콜백에서 예외 던지면 micrometer 가 메트릭을 제거하므로 0 으로 폴백.
    private fun safeCount(status: OutboxStatus): Long =
        try { outboxRepository.countByStatus(status) } catch (_: Exception) { 0L }

    private fun safeLagMs(): Long = try {
        val oldest = outboxRepository.oldestPendingCreatedAt() ?: return 0L
        Duration.between(oldest, Instant.now()).toMillis().coerceAtLeast(0L)
    } catch (_: Exception) { 0L }
}
