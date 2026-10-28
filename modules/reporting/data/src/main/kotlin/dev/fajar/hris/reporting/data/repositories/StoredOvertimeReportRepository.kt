package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.reporting.data.datasources.OvertimeReportDataSource
import dev.fajar.hris.reporting.data.mappers.toOvertimeReportAggregate
import dev.fajar.hris.reporting.domain.repositories.OvertimeReportRepository
import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import java.time.LocalDate
import java.util.UUID

class StoredOvertimeReportRepository(private val source: OvertimeReportDataSource) :
    OvertimeReportRepository {
    override fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: OvertimeStatus?,
    ) = safeDatabaseCall {
        source
            .aggregate(company, from, until, status?.name)
            .toOvertimeReportAggregate(from, until, status)
    }
}
