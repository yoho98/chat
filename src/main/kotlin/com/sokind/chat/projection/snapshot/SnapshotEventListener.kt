package com.sokind.chat.projection.snapshot

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// commit 후 비동기로 스냅샷 생성을 트리거. 워커 루프 블로킹 회피.
@Component
class SnapshotEventListener(
    private val snapshotService: SnapshotService,
) {
    private val log = LoggerFactory.getLogger(SnapshotEventListener::class.java)

    @Async("projectionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: SnapshotRequested) {

        log.info("Received snapshot: {}", event)
        snapshotService.maybeSnapshot(event.sessionId, event.serverSeq)
    }
}
