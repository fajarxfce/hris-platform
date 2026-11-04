package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpacePushBinding
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpacePushBinding
import dev.fajar.hris.schema.tables.records.CollaborationSpacePushBindingsRecord
import java.time.ZoneOffset.UTC
import java.util.UUID

fun CollaborationSpacePushBindingsRecord.toSpacePushBinding(
    space: UUID,
    account: UUID,
    device: UUID,
): CollaborationSpacePushBinding {
    require(spaceId == space && accountId == account && deviceId == device)
    require(requireNotNull(keyHash).matches(Regex("[a-f0-9]{64}")))
    require(requireNotNull(keyEncrypted).length in 1..2048)
    return CollaborationSpacePushBinding(
            space,
            account,
            device,
            requireNotNull(nativeSessionId),
            requireNotNull(matrixUserId),
            requireNotNull(matrixDeviceId),
            requireNotNull(version),
            requireNotNull(reservedAt).toInstant(),
            publishedAt?.toInstant(),
        )
        .also { require(validCollaborationSpacePushBinding(it)) }
}

fun CollaborationSpacePushBinding.toSpacePushRecord(): CollaborationSpacePushBindingsRecord {
    require(validCollaborationSpacePushBinding(this))
    return CollaborationSpacePushBindingsRecord().also {
        it.spaceId = spaceId
        it.accountId = accountId
        it.deviceId = deviceId
        it.nativeSessionId = nativeSessionId
        it.matrixUserId = matrixUserId
        it.matrixDeviceId = matrixDeviceId
        it.version = version
        it.reservedAt = reservedAt.atOffset(UTC)
        it.publishedAt = publishedAt?.atOffset(UTC)
    }
}
