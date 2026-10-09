package dev.fajar.hris.leave.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.YearMonth
import java.util.UUID

interface LeaveEntitlementRepository {
    fun frequency(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
    ): Result<LeaveAccrualFrequency?>

    fun posting(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        period: YearMonth,
    ): Result<LeaveAccrualPosting?>

    fun postings(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
    ): Result<List<LeaveAccrualPosting>>

    fun createPosting(companyId: UUID, posting: LeaveAccrualPosting): Result<Unit>

    fun closing(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
    ): Result<LeaveYearClosing?>

    fun createClosing(companyId: UUID, closing: LeaveYearClosing): Result<Unit>

    fun closeAccount(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
        expectedVersion: Long,
        closingId: UUID,
    ): Result<Unit>
}
