package com.sokind.chat.api.event

import com.sokind.chat.domain.event.EventIngestService
import com.sokind.chat.domain.event.EventRepository
import com.sokind.chat.domain.event.IngestCommand
import com.sokind.chat.domain.session.SessionService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/sessions/{sessionId}/events")
class EventController(
    private val ingestService: EventIngestService,
    private val sessionService: SessionService,
    private val eventRepository: EventRepository,
) {

    @PostMapping
    fun ingest(
        @PathVariable sessionId: UUID,
        @RequestHeader("X-User-Id") userId: String,
        @Valid @RequestBody request: IngestRequest,
    ): IngestResponse {
        sessionService.get(sessionId) // 없으면 404
        return IngestResponse.of(ingestService.ingest(IngestCommand(
            sessionId = sessionId,
            clientEventId = request.clientEventId!!,
            userId = userId,
            type = request.type!!,
            payload = request.payload,
            clientTs = request.clientTs!!,
        )))
    }

    // 원본 이벤트 조회. limit 1~500(기본 100), 다음 페이지는 응답의 nextFromSeq 사용.
    @GetMapping
    fun list(
        @PathVariable sessionId: UUID,
        @RequestParam(required = false) fromSeq: Long?,
        @RequestParam(required = false) toSeq: Long?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
    ): EventListResponse {
        sessionService.get(sessionId) // 없으면 404
        val capped = limit.coerceIn(1, 500)
        val rows = eventRepository.findRange(sessionId, fromSeq, toSeq, from, to, PageRequest.of(0, capped))
        val nextFromSeq = if (rows.size == capped) rows.last().serverSeq else null
        return EventListResponse(items = rows.map(EventItem::of), nextFromSeq = nextFromSeq)
    }
}
