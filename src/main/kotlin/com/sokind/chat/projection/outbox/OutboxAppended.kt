package com.sokind.chat.projection.outbox

import java.util.UUID

// outbox 에 새 row 가 들어왔다는 신호. commit 후 즉시 처리(빠른 경로)에 사용.
data class OutboxAppended(
    val outboxId: Long,
    val eventSeq: Long,
    val sessionId: UUID,
)
