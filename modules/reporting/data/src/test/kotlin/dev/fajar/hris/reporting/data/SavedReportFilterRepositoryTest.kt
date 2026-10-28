package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.data.datasources.SavedReportFilterDataSource
import dev.fajar.hris.reporting.data.dto.SavedReportFilterRow
import dev.fajar.hris.reporting.data.repositories.StoredSavedReportFilterRepository
import java.time.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SavedReportFilterRepositoryTest {
    private val company = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val at = Instant.parse("2026-10-01T00:00:00Z")
    private val row =
        SavedReportFilterRow(
            company,
            owner,
            id,
            0,
            at,
            at,
            "My report",
            "HEADCOUNT",
            "headcount.v1",
            LocalDate.parse("2026-10-01"),
            null,
            null,
            null,
            listOf(company),
        )

    private class Source : SavedReportFilterDataSource {
        var rows = emptyList<SavedReportFilterRow>()
        var failure: Exception? = null
        var count = 0
        var saved: SavedReportFilterRow? = null

        override fun lock(company: UUID, account: UUID) {
            failure?.let { throw it }
        }

        override fun list(company: UUID, account: UUID, limit: Int): List<SavedReportFilterRow> {
            assertEquals(51, limit)
            failure?.let { throw it }
            return rows
        }

        override fun find(company: UUID, account: UUID, id: UUID): SavedReportFilterRow? {
            failure?.let { throw it }
            return rows.firstOrNull()
        }

        override fun countActive(company: UUID, account: UUID, limit: Int): Int {
            assertEquals(51, limit)
            return count
        }

        override fun save(row: SavedReportFilterRow, expectedVersion: Long?): Int {
            saved = row
            return 1
        }
    }

    @Test
    fun mappingRetainsDetachedOwnerScopeAndKeepsDeletedDefinitionsErased() {
        val source = Source().apply { rows = listOf(row) }
        val store = StoredSavedReportFilterRepository(source)
        val value = (store.find(company, owner, id) as Result.Success).value!!
        val definition = requireNotNull(value.definition)
        assertEquals(listOf(company), definition.parameters.companies)
        assertThrows(UnsupportedOperationException::class.java) {
            (definition.parameters.companies as MutableList).clear()
        }
        assertEquals(Result.Success(true), store.save(value, null))
        assertEquals(row, source.saved)
        val deleted = value.copy(version = 10000, definition = null)
        assertEquals(Result.Success(true), store.save(deleted, 9999))
        source.rows = listOf(source.saved!!)
        assertEquals(deleted, (store.find(company, owner, id) as Result.Success).value)
        assertEquals("database_failure", (store.list(company, owner) as Result.Failed).failure.code)
    }

    @Test
    fun foreignScopeMalformedDefinitionsAndUnexpectedIdentityFailWithinTheMappingBoundary() {
        val source = Source()
        val store = StoredSavedReportFilterRepository(source)
        for (malformed in
            listOf(
                row.copy(companyId = UUID.randomUUID()),
                row.copy(accountId = UUID.randomUUID()),
                row.copy(id = UUID.randomUUID()),
                row.copy(version = -1),
                row.copy(version = 10000),
                row.copy(updatedAt = at.minusSeconds(1)),
                row.copy(name = ""),
                row.copy(name = " name "),
                row.copy(kind = "UNKNOWN"),
                row.copy(definitionVersion = "headcount.v2"),
                row.copy(companies = emptyList()),
                row.copy(companies = listOf(company, company)),
                row.copy(status = "PENDING"),
                row.copy(name = null),
            )) {
            source.rows = listOf(malformed)
            assertEquals(
                "database_failure",
                (store.find(company, owner, id) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun overfetchAndCountBoundsCannotSilentlyDropSavedSelections() {
        val source = Source()
        val store = StoredSavedReportFilterRepository(source)
        for (rows in listOf(List(51) { row.copy(id = UUID.randomUUID()) }, listOf(row, row))) {
            source.rows = rows
            assertEquals(
                "database_failure",
                (store.list(company, owner) as Result.Failed).failure.code,
            )
        }
        for (count in listOf(-1, 52)) {
            source.count = count
            assertEquals(
                "database_failure",
                (store.countActive(company, owner) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun cancellationAndInterruptionPropagateAndPrivateSourceFailuresRemainSanitized() {
        val source = Source()
        val store = StoredSavedReportFilterRepository(source)
        for (error in listOf(InterruptedException(), CancellationException())) {
            source.failure = error
            assertThrows(error.javaClass) { store.list(company, owner) }
            assertThrows(error.javaClass) { store.find(company, owner, id) }
            assertThrows(error.javaClass) { store.lock(company, owner) }
        }
        source.failure = IllegalStateException("private saved selection")
        val failure = (store.list(company, owner) as Result.Failed).failure
        assertEquals("database_failure", failure.code)
        assertFalse(failure.toString().contains("private saved selection"))
    }
}
