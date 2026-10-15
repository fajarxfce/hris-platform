package dev.fajar.hris.leave.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.data.datasources.LeaveCalendarDataSource
import dev.fajar.hris.leave.data.mappers.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveCalendarRepository
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class StoredLeaveCalendarRepository(private val source: LeaveCalendarDataSource) :
    LeaveCalendarRepository {
    override fun month(
        companyId: UUID,
        managerAccountId: UUID?,
        accessDate: LocalDate,
        month: YearMonth,
    ): Result<List<LeaveCalendarDay>> = safeDatabaseCall {
        val rows =
            source.month(
                companyId,
                managerAccountId,
                accessDate,
                month.atDay(1),
                month.atEndOfMonth(),
            )
        require(rows.size <= month.lengthOfMonth() && rows.all { YearMonth.from(it.date) == month })
        require(rows.zipWithNext().all { (a, b) -> a.date < b.date })
        rows.map { it.toCalendarDay() }
    }

    override fun entries(
        companyId: UUID,
        managerAccountId: UUID?,
        accessDate: LocalDate,
        date: LocalDate,
        after: UUID?,
        limit: Int,
    ): Result<Page<LeaveCalendarEntry>> = safeDatabaseCall {
        val rows = source.entries(companyId, managerAccountId, accessDate, date, after, limit + 1)
        require(rows.size <= limit + 1)
        require(rows.map { it.requestId }.distinct().size == rows.size)
        Page(
            rows.take(limit).map { it.toCalendarEntry() },
            if (rows.size > limit) rows[limit - 1].requestId.toString() else null,
        )
    }
}
