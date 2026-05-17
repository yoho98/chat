package com.sokind.chat.domain.timeline

import com.sokind.chat.domain.participant.Presence

data class ParticipantView(val userId: String, val presence: Presence)
