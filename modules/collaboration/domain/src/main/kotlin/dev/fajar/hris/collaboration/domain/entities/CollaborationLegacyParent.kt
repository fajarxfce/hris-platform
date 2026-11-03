package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Owned public metadata only. A fingerprint does not prove that this client has private keys. */
data class CollaborationLegacyParent(
    val device: CollaborationLegacyDevice,
    val nativeSessionId: UUID,
    val boundAt: Instant,
    val key: CollaborationLegacyParentKey?,
) {
    override fun toString() = "CollaborationLegacyParent(<metadata>)"
}

data class CollaborationLegacyParentKey(
    val fingerprint: String,
    val version: Long,
    val reservedAt: Instant,
    val publishedAt: Instant?,
) {
    override fun toString() = "CollaborationLegacyParentKey(<metadata>)"
}
