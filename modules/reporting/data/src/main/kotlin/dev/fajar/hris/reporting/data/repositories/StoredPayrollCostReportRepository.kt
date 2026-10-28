package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.reporting.data.datasources.PayrollCostReportDataSource
import dev.fajar.hris.reporting.data.mappers.toPayrollCostReportAggregate
import dev.fajar.hris.reporting.domain.repositories.PayrollCostReportRepository
import java.time.YearMonth
import java.util.UUID

class StoredPayrollCostReportRepository(private val source: PayrollCostReportDataSource) :
    PayrollCostReportRepository {
    override fun aggregate(company: UUID, from: YearMonth, until: YearMonth) = safeDatabaseCall {
        source
            .aggregate(company, from.atDay(1), until.atDay(1))
            .toPayrollCostReportAggregate(from, until)
    }
}
