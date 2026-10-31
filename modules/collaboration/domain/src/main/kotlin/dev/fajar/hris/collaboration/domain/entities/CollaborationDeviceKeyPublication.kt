package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Public identity metadata only. Neither a private key nor device verification trust. */
data class CollaborationDeviceKeyPublication(
    val companyId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val fingerprint: String,
    val version: Long,
    val reservedAt: Instant,
    val publishedAt: Instant?,
)
