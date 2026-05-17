package com.sokind.chat.projection.snapshot

import java.io.Serializable
import java.util.UUID

data class SnapshotId(
    val sessionId: UUID = UUID(0L, 0L),
    val upToSeq: Long = 0L,
) : Serializable
