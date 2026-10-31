package dev.fajar.hris.collaboration.data.crypto

import java.time.Instant
import java.util.UUID

/** Keep recovery_set.py's schema-106/107 projections in sync with this persisted AAD contract. */
fun collaborationProviderPurpose(
    company: UUID,
    account: UUID,
    device: UUID,
    generation: Long,
    expiresAt: Instant? = null,
): String {
    val base = "hris:matrix:$company:$account:$device:$generation"
    if (expiresAt == null) return base
    // PostgreSQL stores microseconds; bind precisely the persisted deadline, including pre-epoch.
    val micros =
        Math.addExact(Math.multiplyExact(expiresAt.epochSecond, 1_000_000L), expiresAt.nano / 1000L)
    return "$base:expires:$micros"
}
