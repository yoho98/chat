package com.sokind.chat.api.snapshot

import com.sokind.chat.domain.session.SessionService
import com.sokind.chat.projection.snapshot.SnapshotService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 운영/디버깅용 수동 스냅샷 트리거. 자동 N개 단위 스냅샷과 별개로 즉시 저장.
@RestController
@RequestMapping("/sessions/{id}/snapshots")
class SnapshotController(
    private val sessionService: SessionService,
    private val snapshotService: SnapshotService,
) {

    @PostMapping
    fun create(@PathVariable id: UUID): SnapshotResponse {
        sessionService.get(id) // 없으면 404
        return SnapshotResponse.of(snapshotService.forceSnapshot(id))
    }
}
