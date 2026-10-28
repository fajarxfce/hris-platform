package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.ReportExportExpiryDataSource
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.data.repositories.StoredReportExportExpiryRepository
import dev.fajar.hris.reporting.domain.entities.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException

class ReportExportExpiryRepositoryTest {
    private val at = Instant.parse("2026-10-28T00:00:00Z")
    private val key =
        ReportExportExpiryKey(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            at.minusSeconds(60),
            at,
        )
    private val row =
        ReportExportRetirementRow(
            key.companyId,
            key.accountId,
            key.id,
            UUID.randomUUID(),
            "QUEUED",
            0,
            at.minusSeconds(120),
            key.expiresAt,
            null,
        )

    private inner class Source : ReportExportExpiryDataSource {
        var found: ReportExportRetirementRow? = row
        var selected =
            mutableListOf(
                ReportExportExpiryRow(
                    key.companyId,
                    key.accountId,
                    key.id,
                    key.expiresAt,
                    key.dueAt,
                )
            )
        var failure: Exception? = null
        var observed: Instant? = null
        var writes = 1

        override fun due(at: Instant, limit: Int): List<ReportExportExpiryRow> {
            failure?.let { throw it }
            return selected
        }

        override fun inspect(
            company: UUID,
            account: UUID,
            id: UUID,
            lock: Boolean,
        ): ReportExportRetirementRow? {
            failure?.let { throw it }
            return found
        }

        override fun lockDue(company: UUID, account: UUID, id: UUID, at: Instant) = true

        override fun stop(row: ReportExportRetirementRow, at: Instant) = writes

        override fun retire(row: ReportExportRetirementRow, at: Instant) = writes

        override fun postpone(company: UUID, account: UUID, id: UUID, at: Instant) = writes

        override fun retry(
            company: UUID,
            account: UUID,
            id: UUID,
            observed: Instant,
            at: Instant,
        ): Int {
            this.observed = observed
            return writes
        }

        override fun remove(company: UUID, account: UUID, id: UUID) = writes
    }

    @Test
    fun discoveryIsBoundedDetachedAndRejectsDuplicateOrFutureDescriptors() {
        val source = Source()
        val repository = StoredReportExportExpiryRepository(source)
        val captured = (repository.due(at, 4) as Result.Success).value
        source.selected.clear()
        assertEquals(listOf(key), captured)
        assertTrue(repository.due(at, 5) is Result.Failed)
        source.selected =
            mutableListOf(
                ReportExportExpiryRow(
                    key.companyId,
                    key.accountId,
                    key.id,
                    key.expiresAt,
                    key.dueAt,
                ),
                ReportExportExpiryRow(
                    key.companyId,
                    key.accountId,
                    key.id,
                    key.expiresAt,
                    key.dueAt,
                ),
            )
        assertTrue(repository.due(at, 4) is Result.Failed)
        source.selected = mutableListOf(source.selected.first().copy(due = at.plusSeconds(1)))
        assertTrue(repository.due(at, 4) is Result.Failed)
    }

    @Test
    fun retirementMappingValidatesScopeAndStateWithoutBusinessPayloads() {
        val source = Source()
        val repository = StoredReportExportExpiryRepository(source)
        assertEquals(
            Result.Success(
                ReportExportRetirement(
                    row.company,
                    row.account,
                    row.id,
                    row.job,
                    ReportExportStatus.QUEUED,
                    0,
                    row.updated,
                    row.expires,
                    null,
                )
            ),
            repository.inspect(key),
        )
        for (invalid in
            listOf(
                row.copy(account = UUID.randomUUID()),
                row.copy(status = "unknown"),
                row.copy(version = 10000),
                row.copy(expires = at),
                row.copy(retired = at),
            )) {
            source.found = invalid
            assertTrue(repository.inspect(key) is Result.Failed)
        }
    }

    @Test
    fun failuresPreserveCancellationAndDatabaseClassification() {
        val source = Source()
        val repository = StoredReportExportExpiryRepository(source)
        source.failure = CancellationException()
        assertThrows(CancellationException::class.java) { repository.due(at, 1) }
        source.failure = InterruptedException()
        assertThrows(InterruptedException::class.java) { repository.inspect(key) }
        source.failure =
            DataAccessResourceFailureException(
                "unavailable",
                java.sql.SQLException("offline", "08006"),
            )
        assertEquals(
            FailureKind.UNAVAILABLE,
            (repository.inspect(key) as Result.Failed).failure.kind,
        )
    }

    @Test
    fun retryMatchesTheObservedScheduleAndRejectsImpossibleWriteCounts() {
        val source = Source()
        val repository = StoredReportExportExpiryRepository(source)
        assertEquals(Result.Success(true), repository.retry(key, at.plusSeconds(60)))
        assertEquals(key.dueAt, source.observed)
        source.writes = 0
        assertEquals(Result.Success(false), repository.retry(key, at.plusSeconds(60)))
        source.writes = 2
        assertTrue(repository.retry(key, at.plusSeconds(60)) is Result.Failed)
    }
}
