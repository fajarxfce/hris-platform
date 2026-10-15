package dev.fajar.hris.leave.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.leave.domain.entities.*
import java.util.UUID

interface LeaveCarryRepository {
    /** The caller holds the employee ledger guard; a request spans at most two years. */
    fun buckets(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        years: Set<Int>,
    ): Result<List<LeaveCarryBucket>>

    fun allocations(companyId: UUID, requestId: UUID): Result<List<LeaveCarryAllocation>>

    fun create(companyId: UUID, award: LeaveCarryAward): Result<Unit>

    fun allocate(
        companyId: UUID,
        requestId: UUID,
        allocations: List<LeaveCarryAllocation>,
    ): Result<Unit>

    fun append(companyId: UUID, postings: List<LeaveCarryPosting>): Result<Unit>
}
