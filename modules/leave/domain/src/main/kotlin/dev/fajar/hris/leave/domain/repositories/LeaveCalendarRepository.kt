package dev.fajar.hris.leave.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

interface LeaveCalendarRepository {
    fun month(
        companyId: UUID,
        managerAccountId: UUID?,
        accessDate: LocalDate,
        month: YearMonth,
    ): Result<List<LeaveCalendarDay>>

    fun entries(
        companyId: UUID,
        managerAccountId: UUID?,
        accessDate: LocalDate,
        date: LocalDate,
        after: UUID?,
        limit: Int,
    ): Result<Page<LeaveCalendarEntry>>
}
