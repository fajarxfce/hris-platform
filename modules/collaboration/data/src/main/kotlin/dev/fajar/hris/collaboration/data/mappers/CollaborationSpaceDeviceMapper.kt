package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceDevicesRecord
import java.time.ZoneOffset
import java.util.UUID

fun spaceDeviceFromRow(
    row: CollaborationSpaceDevicesRecord,
    spaceId: UUID,
    accountId: UUID,
): CollaborationSpaceDevice {
    require(row.spaceId == spaceId && row.accountId == accountId)
    val id = requireNotNull(row.id)
    val origin = CollaborationSpaceDeviceOrigin.valueOf(requireNotNull(row.origin))
    val legacy =
        if (origin == CollaborationSpaceDeviceOrigin.LEGACY)
            CollaborationLegacyDevicePosition(
                requireNotNull(row.legacyCompanyId),
                requireNotNull(row.legacyDeviceId),
            )
        else {
            require(
                row.legacyCompanyId == null &&
                    row.legacyDeviceId == null &&
                    row.legacyKeyFingerprint == null
            )
            null
        }
    if (legacy != null)
        require(requireNotNull(row.legacyKeyFingerprint).matches(Regex("[a-f0-9]{64}")))
    val user = requireNotNull(row.matrixUserId)
    require(user.length <= 255 && validCollaborationSpaceServer(user.substringAfter(':', "")))
    val account = accountId.toString().replace("-", "")
    val prefix = user.substringBefore(':')
    require(
        if (legacy != null)
            prefix == "@" + collaborationMatrixLocalpart(legacy.companyId, accountId)
        else
            prefix == "@" + collaborationSpaceMatrixLocalpart(spaceId, accountId) ||
                prefix.matches(Regex("@hris_[a-f0-9]{32}_$account"))
    )
    val matrixDevice = requireNotNull(row.matrixDeviceId)
    require(
        matrixDevice ==
            if (legacy != null) collaborationMatrixDeviceId(legacy.deviceId)
            else "HRISG_" + id.toString().replace("-", "")
    )
    val platform = CollaborationPlatform.valueOf(requireNotNull(row.platform))
    require(platform != CollaborationPlatform.WEB)
    val label = requireNotNull(row.label)
    require(label.length in 1..64 && label == label.trim() && label.none(Char::isISOControl))
    val state = CollaborationDeviceState.valueOf(requireNotNull(row.state))
    val version = requireNotNull(row.version)
    val created = requireNotNull(row.createdAt).toInstant()
    val updated = requireNotNull(row.updatedAt).toInstant()
    val revoked = row.revokedAt?.toInstant()
    require(version in 0..999999 && updated >= created)
    require((state == CollaborationDeviceState.REVOKED) == (revoked != null))
    require(revoked == null || (revoked >= created && revoked <= updated))
    return CollaborationSpaceDevice(
        id,
        spaceId,
        accountId,
        user,
        matrixDevice,
        origin,
        legacy,
        row.legacyKeyFingerprint,
        requireNotNull(row.originNativeSessionId),
        platform,
        label,
        state,
        version,
        created,
        updated,
        revoked,
    )
}

fun spaceDeviceToRow(device: CollaborationSpaceDevice): CollaborationSpaceDevicesRecord =
    CollaborationSpaceDevicesRecord().apply {
        spaceId = device.spaceId
        id = device.id
        accountId = device.accountId
        matrixUserId = device.matrixUserId
        matrixDeviceId = device.matrixDeviceId
        origin = device.origin.name
        legacyCompanyId = device.legacy?.companyId
        legacyDeviceId = device.legacy?.deviceId
        legacyKeyFingerprint = device.legacyKeyFingerprint
        originNativeSessionId = device.originNativeSessionId
        platform = device.platform.name
        label = device.label
        state = device.state.name
        version = device.version
        createdAt = device.createdAt.atOffset(ZoneOffset.UTC)
        updatedAt = device.updatedAt.atOffset(ZoneOffset.UTC)
        revokedAt = device.revokedAt?.atOffset(ZoneOffset.UTC)
    }
