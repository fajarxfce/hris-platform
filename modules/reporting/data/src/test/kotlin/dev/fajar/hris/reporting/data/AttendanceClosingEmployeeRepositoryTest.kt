package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.AttendanceClosingReportDataSource
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.data.repositories.StoredAttendanceClosingReportRepository
import dev.fajar.hris.reporting.domain.policies.parseAttendanceClosingEmployeeCursor
import java.time.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AttendanceClosingEmployeeRepositoryTest {
    private val company = UUID.randomUUID()
    private val month = YearMonth.parse("2026-09")
    private val closed = OffsetDateTime.parse("2026-10-01T00:00:00Z")
    private val facts = AttendanceClosingFactsRow(0, 0, 0, 0, 30, 0)
    private val report =
        AttendanceClosingReportRow(
            month.atDay(1),
            UUID.randomUUID(),
            UUID.randomUUID(),
            2,
            closed,
            "Asia/Jakarta",
            AttendanceClosingCountsRow(1, 1, 1, 0, 30, 0, 0, facts, facts),
        )
    private val first =
        AttendanceClosingEmployeeRow(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "E1",
            "Fictional employee",
            null,
            0,
            null,
            report,
        )
    private val second =
        first.copy(
            employeeId = UUID.fromString("f0000000-0000-0000-0000-000000000002"),
            employeeNumber = "E2",
        )
    private var rows: () -> List<AttendanceClosingEmployeeRow> = { listOf(first, second) }
    private var observedLimit = 0
    private val repo =
        StoredAttendanceClosingReportRepository(
            object : AttendanceClosingReportDataSource {
                override fun aggregate(
                    company: UUID,
                    from: LocalDate,
                    until: LocalDate,
                ): List<AttendanceClosingReportRow> = error("Unexpected aggregate")

                override fun employees(
                    company: UUID,
                    month: LocalDate,
                    after: UUID?,
                    limit: Int,
                ): List<AttendanceClosingEmployeeRow> {
                    assertEquals(this@AttendanceClosingEmployeeRepositoryTest.company, company)
                    assertEquals(this@AttendanceClosingEmployeeRepositoryTest.month.atDay(1), month)
                    observedLimit = limit
                    return rows()
                }
            }
        )

    @Test
    fun boundedPageHasStableUnsignedUuidOrderAndDetachesItsItems() {
        val data = mutableListOf(first, second)
        rows = { data }
        val page = (repo.employees(company, month, null, 1) as Result.Success).value
        assertEquals(2, observedLimit)
        assertEquals(listOf(first.employeeId), page.items.map { it.employeeId })
        assertEquals(
            Result.Success(first.employeeId),
            parseAttendanceClosingEmployeeCursor(company, month, page.nextCursor),
        )
        data.clear()
        assertEquals(1, page.items.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (page.items as MutableList).clear()
        }
        rows = { listOf(second) }
        val next = (repo.employees(company, month, first.employeeId, 1) as Result.Success).value
        assertEquals(second.employeeId, next.items.single().employeeId)
        assertNull(next.nextCursor)
    }

    @Test
    fun currentCountsRetainTheExactSelectedAdjustmentAndOriginalClosing() {
        val id = UUID.randomUUID()
        val revised =
            first.copy(
                adjustmentId = id,
                adjustmentRevision = 2,
                adjustmentAt = closed.plusDays(1),
                report =
                    report.copy(
                        counts =
                            report.counts.copy(
                                adjustedEmploymentMonths = 1,
                                current = AttendanceClosingFactsRow(1, 0, 0, 0, 29, 420),
                            )
                    ),
            )
        rows = { listOf(revised) }
        val item = (repo.employees(company, month, null, 50) as Result.Success).value.items.single()
        assertEquals(id, item.adjustment?.id)
        assertEquals(2L, item.adjustment?.revision)
        assertEquals(report.jobId, item.closing.jobId)
        assertEquals(0, item.counts.original.acceptedMinutes)
        assertEquals(420, item.counts.current.acceptedMinutes)
    }

    @Test
    fun malformedReferencesCountsAndLabelsAreSanitizedInsideTheAcquisitionBoundary() {
        val invalid =
            listOf(
                first.copy(employeeNumber = ""),
                first.copy(employeeName = "x".repeat(201)),
                first.copy(adjustmentRevision = 1),
                first.copy(adjustmentId = UUID.randomUUID()),
                first.copy(adjustmentAt = closed),
                first.copy(report = report.copy(month = month.atDay(2))),
                first.copy(report = report.copy(periodId = null)),
                first.copy(report = report.copy(timezone = "invalid")),
                first.copy(report = report.copy(invalidSources = 1)),
                first.copy(report = report.copy(counts = report.counts.copy(days = 31))),
                first.copy(report = report.copy(counts = report.counts.copy(employments = 2))),
                first.copy(
                    adjustmentId = UUID.randomUUID(),
                    adjustmentRevision = 1,
                    adjustmentAt = closed.minusSeconds(1),
                    report = report.copy(counts = report.counts.copy(adjustedEmploymentMonths = 1)),
                ),
            )
        for (row in invalid) {
            rows = { listOf(row) }
            assertEquals(
                "database_failure",
                (repo.employees(company, month, null, 50) as Result.Failed).failure.code,
            )
        }
        for (items in
            listOf(
                listOf(second, first),
                listOf(first, first),
                listOf(first, second.copy(report = report.copy(jobId = UUID.randomUUID()))),
                List(52) { first },
            )) {
            rows = { items }
            assertInstanceOf(Result.Failed::class.java, repo.employees(company, month, null, 50))
        }
        rows = { listOf(first) }
        assertInstanceOf(
            Result.Failed::class.java,
            repo.employees(company, month, first.employeeId, 50),
        )
    }

    @Test
    fun cancellationPropagatesAndAnExplicitRetryCanReadAnEmptyPage() {
        rows = { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { repo.employees(company, month, null, 50) }
        rows = { throw InterruptedException("interrupted") }
        try {
            assertThrows(InterruptedException::class.java) {
                repo.employees(company, month, null, 50)
            }
        } finally {
            Thread.interrupted()
        }
        rows = { throw IllegalStateException("private") }
        assertEquals(
            "database_failure",
            (repo.employees(company, month, null, 50) as Result.Failed).failure.code,
        )
        rows = { emptyList() }
        val page = (repo.employees(company, month, null, 50) as Result.Success).value
        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }
}
