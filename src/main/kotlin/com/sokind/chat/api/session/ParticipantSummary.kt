package com.sokind.chat.api.session

import com.sokind.chat.domain.participant.Participant
import com.sokind.chat.domain.participant.Presence
import java.time.Instant

// 참여자 목록 응답. 캐시(참여자 테이블) 기반이라 최신 fold 대비 최대 200ms 지연 가능.
data class ParticipantSummary(
    val userId: String,
    val presence: Presence,
    val joinedAt: Instant,
    val leftAt: Instant?,
) {
    companion object {
        fun of(participant: Participant) = ParticipantSummary(
            userId = participant.userId,
            presence = participant.presence,
            joinedAt = participant.joinedAt,
            leftAt = participant.leftAt,
        )
    }
}
