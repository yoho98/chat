package com.sokind.chat.api.snapshot

import com.sokind.chat.projection.snapshot.SnapshotResult
import java.time.Instant
import java.util.UUID

// 수동 스냅샷 응답. created=false 는 이벤트 없음 또는 같은 지점에 이미 있음.
data class SnapshotResponse(
    val sessionId: UUID,
    val upToSeq: Long,
    val created: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun of(result: SnapshotResult) = SnapshotResponse(
            sessionId = result.sessionId,
            upToSeq   = result.upToSeq,
            created   = result.created,
            createdAt = result.createdAt,
        )
    }
}
