package com.sokind.chat.projection.worker

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// 주기적 폴링. 실제 처리는 ProjectionService 가 담당 (@Transactional 프록시 적용 위해 분리).
@Component
class ProjectionWorker(
    private val projection: ProjectionService,
) {
    private val log = LoggerFactory.getLogger(ProjectionWorker::class.java)

    @Scheduled(fixedDelayString = "\${projection.tick-ms:200}")
    fun tick() {
        try {
            projection.processBatch()
        } catch (exception: Exception) {
            log.warn("projection tick error", exception)
        }
    }
}
