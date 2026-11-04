package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceRequestCoordinatesData
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceRequestLeasesRecord
import java.time.ZoneOffset.UTC
import java.util.UUID

fun CollaborationSpaceRequestOwner.toSpaceRequestCoordinates():
    CollaborationSpaceRequestCoordinatesData {
    require(listOf(spaceId, accountId, deviceId, nativeSessionId).none { it == UUID(0, 0) })
    return CollaborationSpaceRequestCoordinatesData(spaceId, accountId, deviceId, nativeSessionId)
}

fun validateSpaceRequestPosition(position: CollaborationSpaceRequestPosition) {
    position.owner.toSpaceRequestCoordinates()
    when (position.kind) {
        CollaborationSpaceRequestKind.PROVIDER_RENEWAL ->
            require(position.gatewayId == null && position.gatewayVersion == null)
        CollaborationSpaceRequestKind.GATEWAY ->
            require(
                position.gatewayId != null &&
                    position.gatewayId != UUID(0, 0) &&
                    position.gatewayVersion?.let { it in 0..999999 } == true
            )
    }
}

fun CollaborationSpaceRequestLeasesRecord.toSpaceRequestLease(
    expected: CollaborationSpaceRequestOwner,
    expectedSlot: Int,
): CollaborationSpaceRequestLease {
    expected.toSpaceRequestCoordinates()
    require(
        expectedSlot in 0..7 &&
            slot == expectedSlot &&
            spaceId == expected.spaceId &&
            accountId == expected.accountId &&
            deviceId == expected.deviceId &&
            nativeSessionId == expected.nativeSessionId
    )
    val position =
        CollaborationSpaceRequestPosition(
            expected,
            CollaborationSpaceRequestKind.valueOf(requireNotNull(kind)),
            gatewayId,
            gatewayVersion,
        )
    validateSpaceRequestPosition(position)
    require(
        (expectedSlot == 0) == (position.kind == CollaborationSpaceRequestKind.PROVIDER_RENEWAL)
    )
    val start = requireNotNull(startedAt).toInstant()
    val until = requireNotNull(expiresAt).toInstant()
    require(
        start.nano % 1000 == 0 &&
            until.nano % 1000 == 0 &&
            until > start &&
            until <= start.plusSeconds(60)
    )
    val claimToken = requireNotNull(token).also { require(it != UUID(0, 0)) }
    return CollaborationSpaceRequestLease(position, expectedSlot, claimToken, start, until)
}

fun CollaborationSpaceRequestLease.toSpaceRequestRecord(): CollaborationSpaceRequestLeasesRecord =
    CollaborationSpaceRequestLeasesRecord().also {
        it.spaceId = position.owner.spaceId
        it.accountId = position.owner.accountId
        it.deviceId = position.owner.deviceId
        it.nativeSessionId = position.owner.nativeSessionId
        it.kind = position.kind.name
        it.gatewayId = position.gatewayId
        it.gatewayVersion = position.gatewayVersion
        it.slot = slot
        it.token = token
        it.startedAt = startedAt.atOffset(UTC)
        it.expiresAt = expiresAt.atOffset(UTC)
        require(it.toSpaceRequestLease(position.owner, slot) == this)
    }
