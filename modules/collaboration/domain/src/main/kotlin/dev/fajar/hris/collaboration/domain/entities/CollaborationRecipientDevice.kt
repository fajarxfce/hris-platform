package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Current public delivery eligibility, separate from interactive publisher admission. */
data class CollaborationRecipientDevice(
    val companyId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val fingerprint: String,
    val receiveUntil: Instant,
) {
    override fun toString() = "CollaborationRecipientDevice(<redacted>)"
}
