package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationLegacyParentDataSource
import dev.fajar.hris.collaboration.data.mappers.legacyDeviceFromRow
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationLegacyParentRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.CollaborationSpaceLegacyDevices.COLLABORATION_SPACE_LEGACY_DEVICES
import java.util.UUID

class StoredCollaborationLegacyParentRepository(
    private val source: CollaborationLegacyParentDataSource
) : CollaborationLegacyParentRepository {
    override fun find(
        spaceId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
        position: CollaborationLegacyDevicePosition,
    ): Result<CollaborationLegacyParent?> = safeDatabaseCall {
        source
            .find(spaceId, accountId, nativeSessionId, position.companyId, position.deviceId)
            ?.let { row ->
                require(row.nativeSessionId == nativeSessionId)
                val device =
                    legacyDeviceFromRow(
                        row.into(COLLABORATION_SPACE_LEGACY_DEVICES),
                        spaceId,
                        accountId,
                    )
                require(device.position == position)
                val bound = requireNotNull(row.boundAt).toInstant()
                require(bound >= device.deviceCreatedAt)
                val key =
                    if (
                        row.keyFingerprint == null &&
                            row.keyVersion == null &&
                            row.keyReservedAt == null &&
                            row.keyPublishedAt == null
                    )
                        null
                    else {
                        val fingerprint = requireNotNull(row.keyFingerprint)
                        require(fingerprint.matches(Regex("[a-f0-9]{64}")))
                        val version = requireNotNull(row.keyVersion)
                        val reserved = requireNotNull(row.keyReservedAt).toInstant()
                        val published = row.keyPublishedAt?.toInstant()
                        require(version in 0L..1L && (version == 1L) == (published != null))
                        require(
                            reserved >= device.deviceCreatedAt &&
                                (published == null || published >= reserved)
                        )
                        CollaborationLegacyParentKey(fingerprint, version, reserved, published)
                    }
                CollaborationLegacyParent(device, nativeSessionId, bound, key)
            }
    }
}
