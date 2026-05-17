package com.sokind.chat.support.error

import java.time.Instant

data class ApiError(
    val status: Int,
    val error: String,
    val message: String?,
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        fun of(status: Int, error: String, message: String?) = ApiError(status, error, message)
    }
}
