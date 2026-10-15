package dev.fajar.hris.leave.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.leave.data.datasources.LeaveCarryDataSource
import dev.fajar.hris.leave.data.mappers.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveCarryRepository
import java.util.UUID

class StoredLeaveCarryRepository(private val source: LeaveCarryDataSource) : LeaveCarryRepository {
    override fun buckets(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        years: Set<Int>,
    ): Result<List<LeaveCarryBucket>> = safeDatabaseCall {
        require(years.size in 1..2 && years.all { it in 1900..2200 })
        val rows = source.buckets(companyId, employeeId, typeId, years)
        require(
            rows.size <= years.size &&
                rows.map { it.id }.toSet().size == rows.size &&
                rows.map { it.balanceYear }.toSet().size == rows.size
        )
        require(
            rows.all {
                it.companyId == companyId &&
                    it.employmentId == employeeId &&
                    it.typeId == typeId &&
                    it.balanceYear in years
            }
        )
        java.util.List.copyOf(rows.map { it.toCarryBucket() })
    }

    override fun allocations(companyId: UUID, requestId: UUID): Result<List<LeaveCarryAllocation>> =
        safeDatabaseCall {
            val rows = source.allocations(companyId, requestId)
            require(
                rows.size <= 2 &&
                    rows.map { it.allocation.bucketId }.toSet().size == rows.size &&
                    rows.map { it.year }.toSet().size == rows.size
            )
            require(
                rows.all {
                    it.allocation.companyId == companyId &&
                        it.allocation.requestId == requestId &&
                        it.allocation.halfDays in 1..732 &&
                        it.year in 1901..2200
                }
            )
            java.util.List.copyOf(rows.map { it.toCarryAllocation() })
        }

    override fun create(companyId: UUID, award: LeaveCarryAward): Result<Unit> = safeDatabaseCall {
        source.create(award.toCarryRow(companyId))
    }

    override fun allocate(
        companyId: UUID,
        requestId: UUID,
        allocations: List<LeaveCarryAllocation>,
    ): Result<Unit> = safeDatabaseCall {
        require(allocations.size <= 2)
        source.allocate(allocations.map { it.toCarryRow(companyId, requestId) })
    }

    override fun append(companyId: UUID, postings: List<LeaveCarryPosting>): Result<Unit> =
        safeDatabaseCall {
            require(postings.size <= 6)
            source.append(postings.map { it.toCarryRow(companyId) })
        }
}
