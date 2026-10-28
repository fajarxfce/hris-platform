package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.AttendanceClosingEmployee
import dev.fajar.hris.reporting.domain.entities.AttendanceClosingReportAggregate
import java.time.YearMonth
import java.util.UUID

interface AttendanceClosingReportRepository {
    fun employees(
        company: UUID,
        month: YearMonth,
        after: UUID?,
        limit: Int,
    ): Result<Page<AttendanceClosingEmployee>>

    fun aggregate(
        company: UUID,
        from: YearMonth,
        until: YearMonth,
    ): Result<AttendanceClosingReportAggregate>
}
