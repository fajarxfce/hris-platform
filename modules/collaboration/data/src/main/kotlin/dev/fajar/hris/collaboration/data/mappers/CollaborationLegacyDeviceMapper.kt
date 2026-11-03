package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixDeviceId
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyDevicesRecord
import java.util.UUID

/** Map inside the repository acquisition/failure boundary. No native/provider admission. */
fun legacyDeviceFromRow(
    row: CollaborationSpaceLegacyDevicesRecord,
    spaceId: UUID,
    accountId: UUID,
): CollaborationLegacyDevice {
    require(row.spaceId == spaceId && row.accountId == accountId)
    val company = requireNotNull(row.legacyCompanyId)
    val device = requireNotNull(row.legacyDeviceId)
    val user = requireNotNull(row.matrixUserId)
    val matrixDevice = requireNotNull(row.matrixDeviceId)
    require(
        user.length <= 255 &&
            user.substringBefore(':') == "@" + collaborationMatrixLocalpart(company, accountId)
    )
    require(
        user.substringAfter(':', "").isNotBlank() &&
            user.none { it.isWhitespace() || it.isISOControl() }
    )
    require(matrixDevice == collaborationMatrixDeviceId(device))
    val state = CollaborationDeviceState.valueOf(requireNotNull(row.state))
    val version = requireNotNull(row.deviceVersion)
    val generation = requireNotNull(row.credentialGeneration)
    val created = requireNotNull(row.deviceCreatedAt).toInstant()
    val observed = requireNotNull(row.identityObservedAt).toInstant()
    val revoked = row.deviceRevokedAt?.toInstant()
    val retired = row.credentialRetiredAt?.toInstant()
    require(version in 0..1000000 && generation in 0..1000000 && observed >= created)
    require((state == CollaborationDeviceState.REVOKED) == (revoked != null))
    require(revoked == null || revoked >= created)
    require(retired == null || (state == CollaborationDeviceState.REVOKED && retired >= observed))
    return CollaborationLegacyDevice(
        CollaborationLegacyDevicePosition(company, device),
        accountId,
        user,
        matrixDevice,
        CollaborationPlatform.valueOf(requireNotNull(row.platform)),
        state,
        version,
        generation,
        created,
        observed,
        revoked,
        retired,
    )
}
