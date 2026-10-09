package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.schema.tables.records.NativePushRegistrationsRecord
import java.time.ZoneOffset

fun NativePushRegistrationsRecord.toNativePushRegistration() =
    NativePushRegistration(
        sessionId,
        accountId,
        PushPlatform.valueOf(platform),
        enabled,
        version,
        registeredAt.toInstant(),
        updatedAt.toInstant(),
        expiresAt.toInstant(),
    )

fun NativePushRegistration.toRecord() =
    NativePushRegistrationsRecord().also {
        it.sessionId = sessionId
        it.accountId = accountId
        it.platform = platform.name
        it.enabled = enabled
        it.version = version
        it.registeredAt = registeredAt.atOffset(ZoneOffset.UTC)
        it.updatedAt = updatedAt.atOffset(ZoneOffset.UTC)
        it.expiresAt = expiresAt.atOffset(ZoneOffset.UTC)
    }
