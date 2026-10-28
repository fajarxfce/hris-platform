package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.OvertimeReportAggregate
import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import java.time.LocalDate
import java.util.UUID

interface OvertimeReportRepository {
    fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: OvertimeStatus?,
    ): Result<OvertimeReportAggregate>
}
