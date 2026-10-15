package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.*
import java.time.LocalDate
import java.util.UUID

interface LeaveCalendarDataSource {
    fun month(
        company: UUID,
        manager: UUID?,
        accessDate: LocalDate,
        from: LocalDate,
        until: LocalDate,
    ): List<LeaveCalendarDayRow>

    fun entries(
        company: UUID,
        manager: UUID?,
        accessDate: LocalDate,
        date: LocalDate,
        after: UUID?,
        limit: Int,
    ): List<LeaveCalendarEntryRow>
}
