package dev.fajar.hris.identity.data.datasources

import java.time.Instant

interface SignInAttemptDataSource {
    fun purgeExpired(before: Instant, limit: Int): Int

    fun increment(
        bucketHash: String,
        windowStart: Instant,
        expiresAt: Instant,
        maximum: Int,
    ): Boolean
}
