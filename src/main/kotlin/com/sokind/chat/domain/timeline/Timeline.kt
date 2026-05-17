package com.sokind.chat.domain.timeline

import java.time.Instant

data class Timeline(
    val at: Instant,
    val upToSeq: Long,
    val participants: List<ParticipantView>,
    val messages: List<MessageView>,
)
