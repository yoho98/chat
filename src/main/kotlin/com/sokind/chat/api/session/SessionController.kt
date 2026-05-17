package com.sokind.chat.api.session

import com.sokind.chat.api.event.IngestResponse
import com.sokind.chat.domain.session.SessionService
import com.sokind.chat.domain.session.SessionStatus
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/sessions")
class SessionController(
    private val sessionService: SessionService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(): SessionResponse = SessionResponse.of(sessionService.create())

    @PostMapping("/{id}/join")
    fun join(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id") userId: String,
    ): IngestResponse = IngestResponse.of(sessionService.join(id, userId))

    @PostMapping("/{id}/leave")
    fun leave(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id") userId: String,
    ): IngestResponse = IngestResponse.of(sessionService.leave(id, userId))

    @PostMapping("/{id}/end")
    fun end(@PathVariable id: UUID): SessionResponse =
        SessionResponse.of(sessionService.end(id))

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): SessionResponse =
        SessionResponse.of(sessionService.get(id))

    // 캐시된 참여자 조회 — 전체 fold 없이 빠르게 "지금 누가 있는지" 응답.
    @GetMapping("/{id}/participants")
    fun participants(@PathVariable id: UUID): List<ParticipantSummary> =
        sessionService.participantsOf(id).map(ParticipantSummary::of)

    @GetMapping
    fun list(
        @RequestParam(required = false) status: SessionStatus?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(required = false) userId: String?,
    ): List<SessionResponse> = sessionService.list(status, from, to, userId).map(SessionResponse::of)
}
