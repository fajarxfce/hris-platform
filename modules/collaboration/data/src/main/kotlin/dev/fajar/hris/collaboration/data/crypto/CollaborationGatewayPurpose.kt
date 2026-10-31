package dev.fajar.hris.collaboration.data.crypto

import dev.fajar.hris.schema.tables.records.CollaborationGatewaySessionsRecord
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.HexFormat

/** Canonical bounded context hash keeps the existing identity AEAD's 256-byte AAD limit. */
fun collaborationGatewayPurpose(record: CollaborationGatewaySessionsRecord): String {
    val key =
        when (record.replayKind) {
            "BOOTSTRAP" -> requireNotNull(record.bootstrapOperationId).toString()
            "REFRESH" -> requireNotNull(record.previousRefreshHash)
            else -> error("Invalid gateway replay kind")
        }
    val canonical =
        listOf(
                record.companyId,
                record.accountId,
                record.deviceId,
                record.id,
                record.nativeSessionId,
                record.platform,
                record.clientBuild,
                record.version,
                micros(record.createdAt),
                micros(record.rotatedAt),
                micros(record.expiresAt),
                micros(record.accessExpiresAt),
                requireNotNull(record.accessHash),
                requireNotNull(record.refreshHash),
                record.replayKind,
                key,
                micros(requireNotNull(record.replayUntil)),
            )
            .joinToString(":")
    val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return "hris:matrix-gateway:v1:" + HexFormat.of().formatHex(hash)
}

private fun micros(at: OffsetDateTime): Long {
    val instant = at.toInstant()
    return Math.addExact(Math.multiplyExact(instant.epochSecond, 1000000L), instant.nano / 1000L)
}
