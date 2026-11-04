package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceGatewaySessionsRecord
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

fun CollaborationSpaceGatewaySessionsRecord.toSpaceGatewaySession():
    CollaborationSpaceGatewaySession {
    val value =
        CollaborationSpaceGatewaySession(
            id,
            spaceId,
            accountId,
            deviceId,
            nativeSessionId,
            CollaborationPlatform.valueOf(platform),
            clientBuild,
            version,
            createdAt.toInstant(),
            rotatedAt.toInstant(),
            expiresAt.toInstant(),
            accessExpiresAt.toInstant(),
            revokedAt?.toInstant(),
        )
    validateSpaceGatewaySession(value)
    require(
        listOfNotNull(createdAt, rotatedAt, expiresAt, accessExpiresAt, revokedAt, replayUntil)
            .all { it.nano % 1000 == 0 }
    )
    if (value.revokedAt == null) {
        requireHash(accessHash)
        requireHash(refreshHash)
        require(accessHash != refreshHash)
    } else require(accessHash == null && refreshHash == null && replayEncrypted == null)
    replayUntil?.toInstant()?.let {
        require(
            it > value.rotatedAt &&
                it <= value.rotatedAt.plusSeconds(60) &&
                it <= value.accessExpiresAt
        )
    }
    require(value.version != 0L || (value.createdAt == value.rotatedAt && replayKind != "REFRESH"))
    if (replayEncrypted == null)
        require(replayKind == null && bootstrapOperationId == null && previousRefreshHash == null)
    else {
        require(requireNotNull(replayEncrypted).length in 1..4096 && replayUntil != null)
        when (replayKind) {
            "BOOTSTRAP" -> require(bootstrapOperationId != null && previousRefreshHash == null)
            "REFRESH" -> {
                require(bootstrapOperationId == null)
                requireHash(previousRefreshHash)
                require(previousRefreshHash != refreshHash)
            }
            else -> error("Invalid organization gateway replay")
        }
    }
    return value
}

fun CollaborationSpaceGatewaySession.toSpaceGatewayRecord() =
    CollaborationSpaceGatewaySessionsRecord().also {
        validateSpaceGatewaySession(this)
        it.id = id
        it.spaceId = spaceId
        it.accountId = accountId
        it.deviceId = deviceId
        it.nativeSessionId = nativeSessionId
        it.platform = platform.name
        it.clientBuild = clientBuild
        it.version = version
        it.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        it.rotatedAt = rotatedAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        it.expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        it.accessExpiresAt = accessExpiresAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        it.revokedAt = revokedAt?.truncatedTo(ChronoUnit.MICROS)?.atOffset(ZoneOffset.UTC)
    }

private fun validateSpaceGatewaySession(value: CollaborationSpaceGatewaySession) {
    require(value.platform in setOf(CollaborationPlatform.ANDROID, CollaborationPlatform.IOS))
    require(value.clientBuild in 1..999999999 && value.version in 0..1000000)
    require(value.revokedAt != null || value.version < 1000000)
    require(value.createdAt <= value.rotatedAt && value.rotatedAt < value.accessExpiresAt)
    require(value.accessExpiresAt <= value.expiresAt && value.createdAt < value.expiresAt)
    require(value.expiresAt <= value.createdAt.plusSeconds(31 * 86400L))
    require(value.accessExpiresAt <= value.rotatedAt.plusSeconds(900))
    value.revokedAt?.let { require(it >= value.rotatedAt && value.version > 0) }
}

private fun requireHash(value: String?) {
    require(value?.matches(Regex("[a-f0-9]{64}")) == true)
}
