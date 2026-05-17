package com.sokind.chat.api.event

import com.sokind.chat.domain.event.IngestResult

data class IngestResponse(val serverSeq: Long, val duplicate: Boolean) {
    companion object {
        fun of(result: IngestResult) = IngestResponse(result.serverSeq, result.duplicate)
    }
}
