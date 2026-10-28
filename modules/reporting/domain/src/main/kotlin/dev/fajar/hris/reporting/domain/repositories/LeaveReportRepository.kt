package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveStatus
import dev.fajar.hris.reporting.domain.entities.*
import java.time.LocalDate
import java.util.UUID

interface LeaveReportRepository {
    fun aggregate(company: UUID, from: LocalDate, until: LocalDate): Result<LeaveReportAggregate>

    fun requests(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: LeaveStatus?,
        after: LeaveReportCursor?,
        limit: Int,
    ): Result<Page<LeaveReportRequest>>
}
