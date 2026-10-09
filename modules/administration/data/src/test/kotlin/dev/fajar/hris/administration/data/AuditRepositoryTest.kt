package dev.fajar.hris.administration.data

import dev.fajar.hris.administration.data.datasources.AuditDataSource
import dev.fajar.hris.administration.data.dto.*
import dev.fajar.hris.administration.data.repositories.StoredAuditRepository
import dev.fajar.hris.administration.domain.entities.AuditQuery
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuditRepositoryTest {
    private val company = UUID.randomUUID()
    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private val query = AuditQuery(now.minusSeconds(60), now, null, null, null, null, 2)

    private fun row(id: UUID = UUID.randomUUID(), scope: UUID = company) =
        AuditEventRow(
            id,
            scope,
            UUID.randomUUID(),
            "employment",
            UUID.randomUUID(),
            "people.created",
            UUID.randomUUID(),
            now.minusSeconds(1).atOffset(ZoneOffset.UTC),
        )

    private fun repository(operation: () -> List<AuditEventRow>) =
        StoredAuditRepository(
            object : AuditDataSource {
                override fun find(companyId: UUID, id: UUID) = operation().firstOrNull()

                override fun search(companyId: UUID, query: AuditQueryRow): List<AuditEventRow> {
                    assertEquals(company, companyId)
                    assertEquals(3, query.limit)
                    assertEquals(now.atOffset(ZoneOffset.UTC), query.until)
                    return operation()
                }
            }
        )

    @Test
    fun overfetchYieldsABoundedImmutablePageAndAStableLastItemCursor() {
        val rows = List(3) { row() }
        val page = (repository { rows }.search(company, query) as Result.Success).value
        assertEquals(rows.take(2).map { it.id }, page.items.map { it.id })
        assertEquals(rows[1].id.toString(), page.nextCursor)
        assertThrows(UnsupportedOperationException::class.java) {
            (page.items as MutableList).clear()
        }
        val last = (repository { rows.take(2) }.search(company, query) as Result.Success).value
        assertNull(last.nextCursor)
        assertEquals(
            Result.Success(null),
            repository { emptyList() }.find(company, UUID.randomUUID()),
        )
    }

    @Test
    fun unexpectedScopeOrOversizedBatchesFailInsideTheDataBoundary() {
        for (rows in listOf(listOf(row(scope = UUID.randomUUID())), List(4) { row() })) {
            assertEquals(
                "database_failure",
                (repository { rows }.search(company, query) as Result.Failed).failure.code,
            )
        }
        assertEquals(
            "database_failure",
            (repository { listOf(row(scope = UUID.randomUUID())) }.find(company, UUID.randomUUID())
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun technicalCancellationPropagatesThroughBothReadPaths() {
        for (error in listOf(InterruptedException(), CancellationException())) {
            assertThrows(error.javaClass) {
                repository { throw error }.find(company, UUID.randomUUID())
            }
            assertThrows(error.javaClass) { repository { throw error }.search(company, query) }
        }
    }
}
