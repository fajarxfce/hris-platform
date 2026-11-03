package dev.fajar.hris.collaboration.data.crypto

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceProviderState
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

/** Persisted AAD contract; keep recovery_set.py's V121 SQL projection byte-identical. */
fun collaborationSpaceProviderPurpose(state: CollaborationSpaceProviderState): String {
    val canonical =
        listOf(
                state.spaceId,
                state.accountId,
                state.deviceId,
                state.nativeSessionId,
                state.matrixUserId,
                state.matrixDeviceId,
                state.generation,
                micros(state.createdAt),
                micros(state.updatedAt),
                micros(state.expiresAt),
            )
            .joinToString("\n")
    val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return "hris:matrix-space:v1:" + HexFormat.of().formatHex(hash)
}

private fun micros(at: Instant): Long =
    Math.addExact(Math.multiplyExact(at.epochSecond, 1000000L), at.nano / 1000L)
