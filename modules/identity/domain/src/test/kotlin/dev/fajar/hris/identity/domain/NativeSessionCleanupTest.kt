package dev.fajar.hris.identity.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.NativeSessionCleanupResult
import dev.fajar.hris.identity.domain.repositories.NativeSessionCleanupRepository
import dev.fajar.hris.identity.domain.usecases.MaintainNativeSessions
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NativeSessionCleanupTest {
    private val now = Instant.parse("2026-10-16T05:00:00.123456789Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private class Transactions : TransactionRunner {
        val actors = mutableListOf<Actor>()

        override fun <T> run(actor: Actor, operation: () -> Result<T>): Result<T> {
            actors += actor
            return operation()
        }
    }

    @Test
    fun oneWorkerTransactionOwnsTheElapsedRetentionWindowAndFiniteBatch() {
        val transactions = Transactions()
        var calls = 0
        val report = NativeSessionCleanupResult(1, 2, 3)
        val repository = NativeSessionCleanupRepository { expired, replay, sessions, replays ->
            calls++
            assertEquals(Instant.parse("2026-10-15T05:00:00.123456Z"), expired)
            assertEquals(Instant.parse("2026-10-16T05:00:00.123456Z"), replay)
            assertEquals(4, sessions)
            assertEquals(128, replays)
            Result.Success(report)
        }
        assertEquals(
            Result.Success(report),
            MaintainNativeSessions(repository, transactions, clock).execute(),
        )
        assertEquals(1, calls)
        val actor = transactions.actors.single()
        assertEquals(UUID(0, 0), actor.accountId)
        assertNull(actor.companyId)
        assertTrue(actor.permissions.isEmpty() && actor.platformPermissions.isEmpty())
    }

    @ParameterizedTest
    @CsvSource("0,1", "-1,128", "17,128", "4,0", "4,-1", "4,513")
    fun invalidBoundsCannotStartDatabaseWork(sessions: Int, replays: Int) {
        val transactions = Transactions()
        val repository = NativeSessionCleanupRepository { _, _, _, _ ->
            error("No acquisition expected")
        }
        val result =
            MaintainNativeSessions(repository, transactions, clock).execute(sessions, replays)
        assertEquals("invalid_cleanup_limit", (result as Result.Failed).failure.code)
        assertTrue(transactions.actors.isEmpty())
    }

    @Test
    fun preservesFailureWithoutRetryingOrChangingTheRetentionWindow() {
        val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_busy"))
        var calls = 0
        val repository = NativeSessionCleanupRepository { _, _, _, _ ->
            calls++
            failure
        }
        assertSame(failure, MaintainNativeSessions(repository, Transactions(), clock).execute())
        assertEquals(1, calls)
    }

    @Test
    fun cancellationPropagatesAndAnInterruptedCallerCannotStartWork() {
        val transactions = Transactions()
        val repository = NativeSessionCleanupRepository { _, _, _, _ ->
            throw CancellationException("Owned cancelled fixture")
        }
        val maintain = MaintainNativeSessions(repository, transactions, clock)
        assertThrows(CancellationException::class.java) { maintain.execute() }
        assertEquals(1, transactions.actors.size)
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedException::class.java) { maintain.execute() }
        } finally {
            Thread.interrupted()
        }
        assertEquals(1, transactions.actors.size)
    }
}
