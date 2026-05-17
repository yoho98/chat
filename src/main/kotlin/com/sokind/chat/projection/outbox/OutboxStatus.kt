package com.sokind.chat.projection.outbox

enum class OutboxStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    DEAD
}
