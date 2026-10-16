package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.NativeSessionCleanupResult
import dev.fajar.hris.identity.domain.repositories.NativeSessionCleanupRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Worker-only retention policy; it never reads credentials or changes a live session's access. */
class MaintainNativeSessions(
    private val cleanup: NativeSessionCleanupRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(sessionLimit: Int = 4, replayLimit: Int = 128): Result<NativeSessionCleanupResult> {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (sessionLimit !in 1..16 || replayLimit !in 1..512)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_cleanup_limit"))
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val worker = Actor(UUID(0, 0), null, emptySet(), now, UUID.randomUUID())
        return transactions.run(worker) {
            cleanup.prune(now.minusSeconds(86400), now, sessionLimit, replayLimit)
        }
    }
}
