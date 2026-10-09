package dev.fajar.hris.leave.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface LeaveRequestRepository {
    fun unresolvedYear(companyId: UUID, employeeId: UUID, typeId: UUID, year: Int): Result<Boolean>

    fun find(companyId: UUID, id: UUID): Result<LeaveRequest?>

    fun list(
        companyId: UUID,
        employeeId: UUID?,
        status: LeaveStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<LeaveRequestSummary>>

    fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<LeaveRequestChange>>

    fun occupancy(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<LeaveOccupancy>>

    fun create(companyId: UUID, request: LeaveRequest): Result<MutationReceipt>

    fun update(
        actor: Actor,
        id: UUID,
        expectedVersion: Long,
        status: LeaveStatus,
        cancellationApprovalId: UUID?,
        kind: LeaveChangeKind,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt>

    fun releaseOccupancy(companyId: UUID, id: UUID, expectedHalfDays: Int): Result<Unit>
}
