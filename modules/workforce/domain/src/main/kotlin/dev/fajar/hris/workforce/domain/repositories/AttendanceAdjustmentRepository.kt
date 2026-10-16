package dev.fajar.hris.workforce.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.util.UUID

interface AttendanceAdjustmentRepository {
    fun latest(companyId: UUID, jobId: UUID, employeeId: UUID): Result<AttendanceAdjustment?>

    fun find(companyId: UUID, id: UUID): Result<AttendanceAdjustment?>

    fun history(
        companyId: UUID,
        jobId: UUID,
        employeeId: UUID,
        before: Long?,
        limit: Int,
    ): Result<Page<AttendanceAdjustmentSummary>>

    fun append(companyId: UUID, adjustment: AttendanceAdjustment): Result<MutationReceipt>
}
