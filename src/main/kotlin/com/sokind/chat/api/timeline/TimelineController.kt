package com.sokind.chat.api.timeline

import com.sokind.chat.domain.timeline.Timeline
import com.sokind.chat.domain.timeline.TimelineService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/sessions/{sessionId}/timeline")
class TimelineController(
    private val timelineService: TimelineService,
) {

    @GetMapping
    fun get(
        @PathVariable sessionId: UUID,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) at: Instant?,
    ): Timeline = timelineService.replay(sessionId, at ?: Instant.now())
}
