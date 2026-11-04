package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.CollaborationLegacyGateway
import dev.fajar.hris.collaboration.domain.entities.CollaborationLegacyRuntimeOwner
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyGatewaysRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

fun legacyGatewayFromRow(
    row: CollaborationSpaceLegacyGatewaysRecord,
    spaceId: UUID,
    owner: CollaborationLegacyRuntimeOwner,
): CollaborationLegacyGateway {
    require(
        row.spaceId == spaceId &&
            row.accountId == owner.accountId &&
            row.legacyCompanyId == owner.companyId &&
            row.legacyDeviceId == owner.deviceId &&
            row.nativeSessionId == owner.nativeSessionId
    )
    val id = requireNotNull(row.gatewayId)
    val version = requireNotNull(row.version)
    val created = time(row.createdAt)
    val rotated = time(row.rotatedAt)
    val revoked = row.revokedAt?.let(::time)
    require(
        version in 0..1000000 &&
            (revoked != null || version < 1000000) &&
            (revoked == null || version > 0) &&
            rotated >= created &&
            (revoked == null || revoked >= rotated)
    )
    return CollaborationLegacyGateway(spaceId, owner, id, version, created, rotated, revoked)
}

private fun time(value: OffsetDateTime?): Instant {
    val at = requireNotNull(value).toInstant()
    require(at.nano % 1000 == 0 && at.epochSecond in -62135596800L..253402300799L)
    return at
}
