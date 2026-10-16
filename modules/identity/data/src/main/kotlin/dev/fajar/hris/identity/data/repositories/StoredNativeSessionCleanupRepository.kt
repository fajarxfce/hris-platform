package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.identity.data.datasources.NativeSessionCleanupDataSource
import dev.fajar.hris.identity.domain.entities.NativeSessionCleanupResult
import dev.fajar.hris.identity.domain.repositories.NativeSessionCleanupRepository
import java.time.Instant

class StoredNativeSessionCleanupRepository(private val source: NativeSessionCleanupDataSource) :
    NativeSessionCleanupRepository {
    override fun prune(
        expiredBefore: Instant,
        replayBefore: Instant,
        sessionLimit: Int,
        replayLimit: Int,
    ) = safeDatabaseCall {
        val result = source.prune(expiredBefore, replayBefore, sessionLimit, replayLimit)
        require(result.deletedSessions in 0..sessionLimit)
        require(
            result.clearedExchangeReplays in 0..replayLimit &&
                result.clearedRefreshReplays in 0..replayLimit
        )
        NativeSessionCleanupResult(
            result.deletedSessions,
            result.clearedExchangeReplays,
            result.clearedRefreshReplays,
        )
    }
}
