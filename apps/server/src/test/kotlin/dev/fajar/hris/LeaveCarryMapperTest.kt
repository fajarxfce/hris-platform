package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.leave.data.datasources.LeaveCarryDataSource
import dev.fajar.hris.leave.data.mappers.toCarryRow
import dev.fajar.hris.leave.data.models.LeaveCarryAllocationRow
import dev.fajar.hris.leave.data.repositories.StoredLeaveCarryRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.leaveCarryValidity
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveCarryMapperTest {
    private val company = UUID.randomUUID()
    private val employee = UUID.randomUUID()
    private val type = UUID.randomUUID()
    private val bucketId = UUID.randomUUID()
    private val request = UUID.randomUUID()

    private fun row(): LeaveCarryBucketsRecord =
        LeaveCarryAward(
                LeaveCarryBucket(
                    bucketId,
                    2027,
                    6,
                    6,
                    validity = (leaveCarryValidity(2027, 3, "Asia/Jakarta") as Result.Success).value,
                ),
                employee,
                type,
                "Asia/Jakarta",
                3,
            )
            .toCarryRow(company)

    private fun repository(
        buckets: () -> List<LeaveCarryBucketsRecord> = { listOf(row()) },
        allocations: () -> List<LeaveCarryAllocationRow> = { emptyList() },
    ) =
        StoredLeaveCarryRepository(
            object : LeaveCarryDataSource {
                override fun buckets(company: UUID, employee: UUID, type: UUID, years: Set<Int>) =
                    buckets()

                override fun allocations(company: UUID, request: UUID) = allocations()

                override fun create(row: LeaveCarryBucketsRecord) =
                    error("Unexpected fixture write")

                override fun allocate(rows: List<LeaveCarryAllocationsRecord>) =
                    error("Unexpected fixture write")

                override fun append(rows: List<LeaveCarryMovementsRecord>) =
                    error("Unexpected fixture write")
            }
        )

    @Test
    fun acquiredRowsCannotChangeAnOwnedDomainSnapshot() {
        val raw = row()
        val result =
            repository(buckets = { listOf(raw) }).buckets(company, employee, type, setOf(2027))
        assertTrue(result is Result.Success)
        val values = (result as Result.Success).value
        raw.availableHalfDays = 2
        raw.expiresAt = raw.expiresAt.plusDays(2)
        assertEquals(6, values.single().availableHalfDays)
        assertEquals("2027-03-31T17:00:00Z", values.single().validity?.expiresAt.toString())
        assertThrows(UnsupportedOperationException::class.java) {
            (values as MutableList<LeaveCarryBucket>).clear()
        }
    }

    @Test
    fun malformedOrForeignRowsFailInsideTheAcquisitionBoundary() {
        val corruptions: List<(LeaveCarryBucketsRecord) -> Unit> =
            listOf(
                { it.companyId = UUID.randomUUID() },
                { it.employmentId = UUID.randomUUID() },
                { it.typeId = UUID.randomUUID() },
                { it.balanceYear = 2028 },
                { it.availableHalfDays = 7 },
                { it.expiresAt = java.time.OffsetDateTime.MAX },
                { it.validThrough = it.validThrough.minusDays(1) },
                { it.validThrough = null },
                { it.validityMonths = null },
            )
        for (corrupt in corruptions) {
            val raw = row().also(corrupt)
            assertTrue(
                repository(buckets = { listOf(raw) }).buckets(company, employee, type, setOf(2027))
                    is Result.Failed
            )
        }
        assertTrue(
            repository(buckets = { listOf(row(), row()) })
                .buckets(company, employee, type, setOf(2027)) is Result.Failed
        )
    }

    @Test
    fun persistedUtcDeadlineSurvivesDifferentCurrentTimezoneRulesWithoutChangingNewAwardValidation() {
        val raw = row()
        raw.expiresAt = raw.expiresAt.plusHours(1)
        val result =
            repository(buckets = { listOf(raw) }).buckets(company, employee, type, setOf(2027))
        assertTrue(result is Result.Success, result.toString())
        val bucket = (result as Result.Success).value.single()
        val retained = requireNotNull(bucket.validity)
        assertEquals(raw.expiresAt.toInstant(), retained.expiresAt)
        assertEquals(raw.validThrough, retained.through)
        assertThrows(IllegalArgumentException::class.java) {
            LeaveCarryAward(bucket, employee, type, "Asia/Jakarta", 3).toCarryRow(company)
        }
    }

    @Test
    fun requestFundingCannotBeReadUnderAnotherRequestOrYear() {
        val raw =
            LeaveCarryAllocationsRecord().also {
                it.companyId = company
                it.requestId = request
                it.bucketId = bucketId
                it.halfDays = 2
            }
        val source = { listOf(LeaveCarryAllocationRow(raw, 2027)) }
        assertEquals(
            Result.Success(listOf(LeaveCarryAllocation(bucketId, 2027, 2))),
            repository(allocations = source).allocations(company, request),
        )
        raw.requestId = UUID.randomUUID()
        assertTrue(repository(allocations = source).allocations(company, request) is Result.Failed)
        raw.requestId = request
        assertTrue(
            repository(allocations = { listOf(LeaveCarryAllocationRow(raw, 2300)) })
                .allocations(company, request) is Result.Failed
        )
    }

    @Test
    fun callerCancellationIsNotConvertedToAnEmptyBucketOrFailure() {
        assertThrows(CancellationException::class.java) {
            repository(buckets = { throw CancellationException("Fixture cancellation") })
                .buckets(company, employee, type, setOf(2027))
        }
    }
}
