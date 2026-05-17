package com.sokind.chat.projection.snapshot

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SnapshotRepository : JpaRepository<Snapshot, SnapshotId> {

    // 가장 최신 스냅샷 조회 (운영 디버깅용)
    fun findTopBySessionIdOrderByUpToSeqDesc(sessionId: UUID): Snapshot?

    // 주어진 seq 이하 중 가장 최신 스냅샷. fold 초기값으로 사용. 없으면 null.
    fun findTopBySessionIdAndUpToSeqLessThanEqualOrderByUpToSeqDesc(
        sessionId: UUID,
        upToSeq: Long,
    ): Snapshot?
}
