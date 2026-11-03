package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceProviderState
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpaceServer
import dev.fajar.hris.schema.tables.records.CollaborationSpaceProviderSessionsRecord
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

fun spaceProviderState(
    row: CollaborationSpaceProviderSessionsRecord,
    space: UUID,
    account: UUID,
    device: UUID,
): CollaborationSpaceProviderState {
    require(row.spaceId == space && row.accountId == account && row.deviceId == device)
    val user = requireNotNull(row.matrixUserId)
    val providerDevice = requireNotNull(row.matrixDeviceId)
    val prefix = user.substringBefore(':')
    val compactAccount = account.toString().replace("-", "")
    val compactSpace = space.toString().replace("-", "")
    require(user.length <= 255 && validCollaborationSpaceServer(user.substringAfter(':', "")))
    require(
        prefix == "@hrisg_${compactSpace}_$compactAccount" ||
            prefix.matches(Regex("@hris_[a-f0-9]{32}_$compactAccount"))
    )
    require(
        providerDevice == "HRISG_" + device.toString().replace("-", "") ||
            providerDevice.matches(Regex("HRIS_[a-f0-9]{32}"))
    )
    val generation = requireNotNull(row.generation)
    val created = requireNotNull(row.createdAt).toInstant()
    val updated = requireNotNull(row.updatedAt).toInstant()
    val expiry = requireNotNull(row.tokenExpiresAt).toInstant()
    val cleared = row.clearedAt?.toInstant()
    require(listOfNotNull(created, updated, expiry, cleared).all { it.nano % 1000 == 0 })
    require(generation in 0..1000000 && updated >= created && expiry > created)
    require((row.tokenEncrypted == null) == (cleared != null))
    if (cleared == null) {
        require(generation < 1000000 && expiry > updated && expiry <= updated.plusSeconds(600))
        require(requireNotNull(row.tokenEncrypted).length in 1..4096)
    } else require(cleared >= created && cleared <= updated)
    return CollaborationSpaceProviderState(
        space,
        account,
        device,
        requireNotNull(row.nativeSessionId),
        user,
        providerDevice,
        generation,
        created,
        updated,
        expiry,
        cleared,
    )
}

fun spaceProviderRow(
    state: CollaborationSpaceProviderState
): CollaborationSpaceProviderSessionsRecord =
    CollaborationSpaceProviderSessionsRecord().apply {
        spaceId = state.spaceId
        accountId = state.accountId
        deviceId = state.deviceId
        nativeSessionId = state.nativeSessionId
        matrixUserId = state.matrixUserId
        matrixDeviceId = state.matrixDeviceId
        generation = state.generation
        createdAt = state.createdAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        updatedAt = state.updatedAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        tokenExpiresAt = state.expiresAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        clearedAt = state.clearedAt?.truncatedTo(ChronoUnit.MICROS)?.atOffset(ZoneOffset.UTC)
    }
