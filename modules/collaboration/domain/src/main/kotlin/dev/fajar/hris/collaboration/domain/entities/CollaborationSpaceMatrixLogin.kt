package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Prepared from admitted canonical/device metadata, never arbitrary public provider identifiers.
 */
data class CollaborationSpaceMatrixLogin(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val canonicalLegacyCompanyId: UUID?,
    val retainedDevice: CollaborationLegacyDevicePosition?,
    val issuedAt: Instant,
    val expiresAt: Instant,
) {
    init {
        require(retainedDevice == null || retainedDevice.companyId == canonicalLegacyCompanyId)
    }

    override fun toString() = "CollaborationSpaceMatrixLogin(<metadata>)"
}
