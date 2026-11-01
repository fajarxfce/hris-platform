package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Retained identity only. A callback still requires current session/device/room admission. */
data class CollaborationPushBinding(
    val companyId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val version: Long,
    val reservedAt: Instant,
    val publishedAt: Instant?,
)

class CollaborationPushKey(val value: String) {
    override fun toString() = "CollaborationPushKey(<redacted>)"
}
