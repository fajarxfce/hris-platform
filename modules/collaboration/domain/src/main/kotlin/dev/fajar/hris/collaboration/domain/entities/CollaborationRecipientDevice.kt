package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Pinned recipient candidate. Every returned provider key still requires signature/pin validation.
 */
data class CollaborationRecipientDevice(
    val companyId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val fingerprint: String,
    val receiveUntil: Instant,
    val publicationConfirmed: Boolean,
) {
    override fun toString() = "CollaborationRecipientDevice(<redacted>)"
}
