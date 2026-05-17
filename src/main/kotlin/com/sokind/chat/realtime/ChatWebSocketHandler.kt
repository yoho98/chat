package com.sokind.chat.realtime

import com.sokind.chat.domain.event.EventIngestService
import com.sokind.chat.domain.event.EventRepository
import com.sokind.chat.domain.event.EventType
import com.sokind.chat.domain.event.IngestCommand
import com.sokind.chat.domain.session.SessionRepository
import com.sokind.chat.realtime.presence.PresenceTracker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Instant
import java.util.UUID

// WebSocket 채팅 처리. /ws/chat?session={uuid}&user={id}[&since={lastSeq}]
// - 접속 시 RECONNECT 이벤트 발행, since 지정 시 누락분 먼저 푸시
// - 같은 유저의 마지막 WS가 끊기면 DISCONNECT 발행
// - JOIN/LEAVE 는 REST 로만 처리 (사용자 명시 의도)
@Component
class ChatWebSocketHandler(
    private val ingestService: EventIngestService,
    private val eventRepository: EventRepository,
    private val sessionRepository: SessionRepository,
    private val presence: PresenceTracker,
    private val mapper: ObjectMapper,
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(ChatWebSocketHandler::class.java)

    private data class Ctx(val sessionId: UUID, val userId: String)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val params = parseParams(session.uri)
        val sessionIdStr = params["session"]
        val userId = params["user"]
        if (sessionIdStr.isNullOrBlank() || userId.isNullOrBlank()) {
            session.close(CloseStatus.BAD_DATA.withReason("session and user are required"))
            return
        }
        val sessionId = try { UUID.fromString(sessionIdStr) } catch (event: Exception) {
            session.close(CloseStatus.BAD_DATA.withReason("session must be UUID")); return
        }
        if (!sessionRepository.existsById(sessionId)) {
            session.close(CloseStatus.BAD_DATA.withReason("session not found")); return
        }

        session.attributes["ctx"] = Ctx(sessionId, userId)
        val firstForUser = presence.attach(sessionId, userId, session)
        val since = params["since"]?.toLongOrNull()

        // RECONNECT 보내기 전에 누락 이벤트부터 푸시 (재생 순서 보장)
        if (since != null) {
            val missed = eventRepository.findSince(sessionId, since)
            for (event in missed) {
                session.sendMessage(TextMessage(mapper.writeValueAsString(envelopeFromEntity(event))))
            }
        }

        if (firstForUser) {
            val result = ingestService.ingest(IngestCommand(
                sessionId = sessionId,
                clientEventId = UUID.randomUUID(),
                userId = userId,
                type = EventType.RECONNECT,
                payload = emptyMap(),
                clientTs = Instant.now(),
            ))
            broadcast(sessionId, envelopeOf(result.serverSeq, result.duplicate, EventType.RECONNECT, userId, emptyMap(), Instant.now()))
        }
        log.debug("WS connect session={} user={} since={} firstForUser={}", sessionId, userId, since, firstForUser)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val ctx = session.attributes["ctx"] as? Ctx ?: return
        val request: Map<String, Any?> = mapper.readValue(message.payload, Map::class.java) as Map<String, Any?>
        val clientEventId = (request["clientEventId"] as? String)?.let { UUID.fromString(it) } ?: UUID.randomUUID()
        val type = (request["type"] as? String)?.let { EventType.valueOf(it) } ?: EventType.MESSAGE
        @Suppress("UNCHECKED_CAST")
        val payload = (request["payload"] as? Map<String, Any?>) ?: emptyMap()
        val clientTs = (request["clientTs"] as? String)?.let { Instant.parse(it) } ?: Instant.now()

        val result = ingestService.ingest(IngestCommand(
            sessionId = ctx.sessionId,
            clientEventId = clientEventId,
            userId = ctx.userId,
            type = type,
            payload = payload,
            clientTs = clientTs,
        ))
        broadcast(ctx.sessionId, envelopeOf(result.serverSeq, result.duplicate, type, ctx.userId, payload, clientTs))
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val ctx = session.attributes["ctx"] as? Ctx ?: return
        val empty = presence.detach(ctx.sessionId, ctx.userId, session)
        if (empty) {
            val result = ingestService.ingest(IngestCommand(
                sessionId = ctx.sessionId,
                clientEventId = UUID.randomUUID(),
                userId = ctx.userId,
                type = EventType.DISCONNECT,
                payload = emptyMap(),
                clientTs = Instant.now(),
            ))
            broadcast(ctx.sessionId, envelopeOf(result.serverSeq, result.duplicate, EventType.DISCONNECT, ctx.userId, emptyMap(), Instant.now()))
        }
        log.debug("WS close session={} user={} empty={} status={}", ctx.sessionId, ctx.userId, empty, status)
    }

    private fun broadcast(sessionId: UUID, envelope: Map<String, Any?>) {
        val json = mapper.writeValueAsString(envelope)
        for (ws in presence.sessionsOf(sessionId)) {
            try {
                if (ws.isOpen) ws.sendMessage(TextMessage(json))
            } catch (exception: Exception) {
                log.warn("WS push failed: {}", exception.message)
            }
        }
    }

    private fun envelopeOf(
        serverSeq: Long, duplicate: Boolean, type: EventType,
        userId: String, payload: Map<String, Any?>, clientTs: Instant,
    ): Map<String, Any?> = mapOf(
        "serverSeq" to serverSeq,
        "duplicate" to duplicate,
        "type" to type.name,
        "userId" to userId,
        "payload" to payload,
        "clientTs" to clientTs.toString(),
    )

    private fun envelopeFromEntity(event: com.sokind.chat.domain.event.DomainEvent): Map<String, Any?> = mapOf(
        "serverSeq" to event.serverSeq,
        "duplicate" to false,
        "type" to event.type.name,
        "userId" to event.userId,
        "payload" to event.payload,
        "clientTs" to event.clientTs.toString(),
        "replay" to true,
    )

    private fun parseParams(uri: URI?): Map<String, String> {
        val q = uri?.rawQuery ?: return emptyMap()
        return q.split("&").mapNotNull {
            val i = it.indexOf('=')
            if (i < 0) null else java.net.URLDecoder.decode(it.substring(0, i), Charsets.UTF_8) to
                java.net.URLDecoder.decode(it.substring(i + 1), Charsets.UTF_8)
        }.toMap()
    }
}
