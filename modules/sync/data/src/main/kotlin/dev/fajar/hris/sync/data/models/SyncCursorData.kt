package dev.fajar.hris.sync.data.models

import java.util.UUID

data class SyncCursorData(
    val schema: Int,
    val accountId: UUID,
    val companyId: UUID,
    val epoch: UUID,
    val fingerprint: String,
    val phase: String,
    val position: Long,
    val issuedAt: Long,
    val expiresAt: Long,
    val upperPosition: Long?,
    val afterCollection: String?,
    val afterId: UUID?,
    val page: Int,
) {
    override fun toString() = "SyncCursorData(<redacted>)"
}
