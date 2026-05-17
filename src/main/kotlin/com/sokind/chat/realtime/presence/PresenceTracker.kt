package com.sokind.chat.realtime.presence

import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// 접속 상태 추적. 단일 인스턴스 전용 (멀티 인스턴스는 Redis 로 확장).
// 한 사용자가 여러 WS(여러 탭) 보유 가능.
// attach: 그 사용자의 첫 WS 면 true → RECONNECT 발행 신호
// detach: 그 사용자의 마지막 WS 면 true → DISCONNECT 발행 신호
@Component
class PresenceTracker {

    private val map = ConcurrentHashMap<UUID, MutableMap<String, MutableSet<WebSocketSession>>>()

    fun attach(sessionId: UUID, userId: String, ws: WebSocketSession): Boolean {
        val users = map.computeIfAbsent(sessionId) { mutableMapOf() }
        synchronized(users) {
            val sessions = users.getOrPut(userId) { mutableSetOf() }
            val firstForUser = sessions.isEmpty()
            sessions.add(ws)
            return firstForUser
        }
    }

    fun detach(sessionId: UUID, userId: String, ws: WebSocketSession): Boolean {
        val users = map[sessionId] ?: return true
        synchronized(users) {
            val sessions = users[userId] ?: return true
            sessions.remove(ws)
            if (sessions.isEmpty()) {
                users.remove(userId)
                if (users.isEmpty()) map.remove(sessionId)
                return true
            }
            return false
        }
    }

    fun sessionsOf(sessionId: UUID): List<WebSocketSession> {
        val users = map[sessionId] ?: return emptyList()
        synchronized(users) {
            return users.values.flatMap { it.toList() }
        }
    }
}
