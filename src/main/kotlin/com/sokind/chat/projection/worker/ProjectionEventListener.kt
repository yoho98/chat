package com.sokind.chat.projection.worker

import com.sokind.chat.projection.outbox.OutboxAppended
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// 빠른 경로: ingest commit 직후 즉시 처리해 지연을 ~0 으로.
// ProjectionWorker 는 보조 경로 (실패/누락된 row 를 5초마다 따라잡기).
// 두 경로 모두 ProjectionService.processBatch 를 호출 → 중복 처리 안전.
// projection.immediate=false 로 끄면 통합 테스트의 결정성 확보.
@Component
@ConditionalOnProperty(name = ["projection.immediate"], havingValue = "true", matchIfMissing = true)
class ProjectionEventListener(
    private val projectionService: ProjectionService,
) {
    private val log = LoggerFactory.getLogger(ProjectionEventListener::class.java)

    @Async("projectionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: OutboxAppended) {
        try {
            projectionService.processBatch()
        } catch (exception: Exception) {
            // 즉시 경로가 실패해도 5초 내 폴링이 같은 row 를 다시 시도
            log.warn("immediate projection failed eventSeq={} reason={}", event.eventSeq, exception.message)
        }
    }
}
