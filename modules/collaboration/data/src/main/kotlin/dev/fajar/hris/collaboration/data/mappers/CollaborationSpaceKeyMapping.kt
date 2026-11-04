package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceKeyPublication
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpaceKey
import dev.fajar.hris.schema.tables.records.CollaborationSpaceKeyPublicationsRecord
import java.time.ZoneOffset.UTC
import java.util.UUID

fun CollaborationSpaceKeyPublicationsRecord.toSpaceKey(
    space: UUID,
    account: UUID,
    device: UUID,
): CollaborationSpaceKeyPublication {
    require(spaceId == space && accountId == account && deviceId == device)
    return CollaborationSpaceKeyPublication(
            space,
            account,
            device,
            requireNotNull(nativeSessionId),
            requireNotNull(matrixUserId),
            requireNotNull(matrixDeviceId),
            requireNotNull(fingerprint),
            requireNotNull(version),
            requireNotNull(reservedAt).toInstant(),
            publishedAt?.toInstant(),
        )
        .also { require(validCollaborationSpaceKey(it)) }
}

fun CollaborationSpaceKeyPublication.toSpaceKeyRecord(): CollaborationSpaceKeyPublicationsRecord {
    require(validCollaborationSpaceKey(this))
    return CollaborationSpaceKeyPublicationsRecord().also {
        it.spaceId = spaceId
        it.accountId = accountId
        it.deviceId = deviceId
        it.nativeSessionId = nativeSessionId
        it.matrixUserId = matrixUserId
        it.matrixDeviceId = matrixDeviceId
        it.fingerprint = fingerprint
        it.version = version
        it.reservedAt = reservedAt.atOffset(UTC)
        it.publishedAt = publishedAt?.atOffset(UTC)
    }
}
