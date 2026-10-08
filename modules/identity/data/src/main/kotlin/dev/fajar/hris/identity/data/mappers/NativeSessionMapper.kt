package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.domain.entities.NativeSession
import dev.fajar.hris.schema.tables.records.NativeSessionsRecord
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun NativeSessionsRecord.toNativeSession(): NativeSession =
    NativeSession(
        id,
        accountId,
        deviceName,
        createdAt.toInstant(),
        authenticatedAt.toInstant(),
        mfaVerifiedAt?.toInstant(),
        credentialVersion,
        expiresAt.toInstant(),
        accessExpiresAt?.toInstant() ?: java.time.Instant.EPOCH,
        rotatedAt.toInstant(),
        revokedAt?.toInstant(),
        version,
        exchangeOperationId,
        exchangeReplayUntil?.toInstant(),
    )

fun NativeSession.toRecord(): NativeSessionsRecord =
    NativeSessionsRecord().also { record ->
        record.id = id
        record.accountId = accountId
        record.deviceName = deviceName
        record.createdAt = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC)
        record.authenticatedAt = OffsetDateTime.ofInstant(authenticatedAt, ZoneOffset.UTC)
        record.mfaVerifiedAt = mfaVerifiedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        record.credentialVersion = credentialVersion
        record.version = version
        record.expiresAt = OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        record.accessExpiresAt = OffsetDateTime.ofInstant(accessExpiresAt, ZoneOffset.UTC)
        record.rotatedAt = OffsetDateTime.ofInstant(rotatedAt, ZoneOffset.UTC)
        record.revokedAt = revokedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        record.exchangeOperationId = exchangeOperationId
        record.exchangeReplayUntil =
            exchangeReplayUntil?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
    }
