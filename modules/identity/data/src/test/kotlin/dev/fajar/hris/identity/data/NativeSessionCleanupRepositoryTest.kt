package dev.fajar.hris.identity.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.NativeSessionCleanupDataSource
import dev.fajar.hris.identity.data.models.NativeSessionCleanupData
import dev.fajar.hris.identity.data.repositories.StoredNativeSessionCleanupRepository
import dev.fajar.hris.identity.domain.entities.NativeSessionCleanupResult
import java.sql.SQLException
import java.time.Instant
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NativeSessionCleanupRepositoryTest {
    private val now = Instant.parse("2026-10-16T05:00:00Z")

    @Test
    fun mapsOnlyBoundedSummaryCountsAndPreservesTheAcquisitionArguments() {
        val repository =
            StoredNativeSessionCleanupRepository(
                NativeSessionCleanupDataSource { expired, replay, sessions, replays ->
                    assertEquals(now.minusSeconds(86400), expired)
                    assertEquals(now, replay)
                    assertEquals(4, sessions)
                    assertEquals(128, replays)
                    NativeSessionCleanupData(4, 128, 127)
                }
            )
        assertEquals(
            Result.Success(NativeSessionCleanupResult(4, 128, 127)),
            repository.prune(now.minusSeconds(86400), now, 4, 128),
        )
    }

    @ParameterizedTest
    @CsvSource("-1,0,0", "5,0,0", "0,-1,0", "0,129,0", "0,0,-1", "0,0,129")
    fun invalidSummariesFailInsideTheDatabaseBoundary(
        deleted: Int,
        exchanges: Int,
        refreshes: Int,
    ) {
        val repository =
            StoredNativeSessionCleanupRepository(
                NativeSessionCleanupDataSource { _, _, _, _ ->
                    NativeSessionCleanupData(deleted, exchanges, refreshes)
                }
            )
        assertEquals(
            "database_failure",
            (repository.prune(now.minusSeconds(86400), now, 4, 128) as Result.Failed).failure.code,
        )
    }

    @Test
    fun technicalDenialKeepsItsSafeClassificationAndCancellationIsNotWrapped() {
        val denied =
            StoredNativeSessionCleanupRepository(
                NativeSessionCleanupDataSource { _, _, _, _ ->
                    throw org.jooq.exception.DataAccessException(
                        "Private fixture SQL",
                        SQLException("Private fixture details", "42501"),
                    )
                }
            )
        assertEquals(
            Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied")),
            denied.prune(now.minusSeconds(86400), now, 4, 128),
        )
        val cancelled =
            StoredNativeSessionCleanupRepository(
                NativeSessionCleanupDataSource { _, _, _, _ ->
                    throw CancellationException("Owned cancelled fixture")
                }
            )
        assertThrows(CancellationException::class.java) {
            cancelled.prune(now.minusSeconds(86400), now, 4, 128)
        }
    }
}
