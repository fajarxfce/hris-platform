package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.schema.tables.records.CollaborationGatewaySessionsRecord
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

fun CollaborationGatewaySessionsRecord.toGatewaySession() =
    CollaborationGatewaySession(
        id,
        companyId,
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

fun CollaborationGatewaySession.toGatewayRecord() =
    CollaborationGatewaySessionsRecord().also {
        it.id = id
        it.companyId = companyId
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
