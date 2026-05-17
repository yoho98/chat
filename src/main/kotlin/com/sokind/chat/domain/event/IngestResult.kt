package com.sokind.chat.domain.event

data class IngestResult(val serverSeq: Long, val duplicate: Boolean)
