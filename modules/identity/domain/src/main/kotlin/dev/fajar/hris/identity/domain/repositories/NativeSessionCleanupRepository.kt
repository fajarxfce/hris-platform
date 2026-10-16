package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.entities.NativeSessionCleanupResult
import java.time.Instant

fun interface NativeSessionCleanupRepository {
    fun prune(
        expiredBefore: Instant,
        replayBefore: Instant,
        sessionLimit: Int,
        replayLimit: Int,
    ): Result<NativeSessionCleanupResult>
}
