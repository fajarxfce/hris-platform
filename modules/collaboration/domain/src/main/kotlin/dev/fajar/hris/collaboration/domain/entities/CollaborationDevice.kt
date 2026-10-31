package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** A company-scoped provider device, bound to one HRIS native session. */
data class CollaborationDevice(
    val id: UUID,
    val companyId: UUID,
    val accountId: UUID,
    val nativeSessionId: UUID,
    val state: CollaborationDeviceState,
    val version: Long,
    val createdAt: Instant,
    val revokedAt: Instant?,
)

enum class CollaborationDeviceState {
    PROVISIONING,
    ACTIVE,
    REVOKED,
}
