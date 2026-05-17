package com.sokind.chat.domain.timeline

// snapshots.state JSON 형태. fold 결과를 저장하고 다시 fold 초기값으로 복원.
data class SnapshotState(
    val participants: List<ParticipantView> = emptyList(),
    val messages: List<MessageView> = emptyList(),
    val upToSeq: Long = 0L,
)
