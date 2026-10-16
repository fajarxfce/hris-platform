package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.data.datasources.PayrollWorkSourceDataSource
import dev.fajar.hris.payroll.data.mappers.toWorkAdjustment
import dev.fajar.hris.payroll.domain.entities.PayrollWorkSource
import dev.fajar.hris.payroll.domain.repositories.PayrollWorkSourceRepository
import java.time.YearMonth
import java.util.UUID

class StoredPayrollWorkSourceRepository(private val source: PayrollWorkSourceDataSource) :
    PayrollWorkSourceRepository {
    override fun find(
        company: UUID,
        employee: UUID,
        month: YearMonth,
        lock: Boolean,
    ): Result<PayrollWorkSource?> = safeDatabaseCall {
        source.period(company, month.atDay(1), lock)?.let { row ->
            val includes =
                row.jobId?.let { source.includesEmployee(company, it, employee) } ?: false
            val adjustment =
                row.jobId
                    ?.let { source.latestAdjustment(company, it, employee) }
                    ?.let {
                        require(includes)
                        it.toWorkAdjustment(month)
                    }
            PayrollWorkSource(
                row.id,
                YearMonth.from(row.month),
                row.version,
                row.status == "CLOSED",
                row.jobId,
                includes,
                adjustment,
            )
        }
    }
}
