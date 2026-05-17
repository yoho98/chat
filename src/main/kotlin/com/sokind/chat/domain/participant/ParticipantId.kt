package com.sokind.chat.domain.participant

import java.io.Serializable
import java.util.UUID

data class ParticipantId(
    val sessionId: UUID = UUID(0L, 0L),
    val userId: String = "",
) : Serializable
