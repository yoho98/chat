package com.sokind.chat.projection.snapshot

import java.util.UUID

// 스냅샷 생성 요청 신호. commit 후 비동기로 처리해 워커 루프를 막지 않음.
data class SnapshotRequested(val sessionId: UUID, val serverSeq: Long)
