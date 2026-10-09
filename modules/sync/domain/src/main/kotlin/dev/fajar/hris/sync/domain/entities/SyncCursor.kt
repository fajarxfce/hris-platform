package dev.fajar.hris.sync.domain.entities

import java.time.Instant
import java.util.UUID

data class SyncCursor(
    val accountId: UUID,
    val companyId: UUID,
    val epoch: UUID,
    val scopeFingerprint: String,
    val phase: SyncCursorPhase,
    val position: Long,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val upperPosition: Long? = null,
    val after: SyncResourceKey? = null,
    val page: Int = 0,
)
