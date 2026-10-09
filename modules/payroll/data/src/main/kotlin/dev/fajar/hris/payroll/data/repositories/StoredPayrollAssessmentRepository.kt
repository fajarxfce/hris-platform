package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollAssessmentDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollAssessmentRepository
import java.time.YearMonth
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredPayrollAssessmentRepository(
    private val source: PayrollAssessmentDataSource,
    private val json: ObjectMapper,
) : PayrollAssessmentRepository {
    override fun taxHistory(company: UUID, id: UUID): Result<PayrollTaxAssessment?> =
        safeDatabaseCall {
            source.taxHistory(company, id)?.toTaxAssessment(json)
        }

    override fun payslip(
        company: UUID,
        id: UUID,
        allowedEmployees: Set<UUID>?,
    ): Result<PayrollPayslip?> = safeDatabaseCall {
        source.payslip(company, id, allowedEmployees)?.toPayslip(json)
    }

    override fun payslips(
        company: UUID,
        from: YearMonth,
        until: YearMonth,
        employeeId: UUID?,
        allowedEmployees: Set<UUID>?,
        after: PayrollPayslipCursor?,
        limit: Int,
    ): Result<Page<PayrollPayslipSummary>> = safeDatabaseCall {
        val rows =
            source.payslips(
                company,
                from.atDay(1),
                until.atDay(1),
                employeeId,
                allowedEmployees,
                after?.month?.atDay(1),
                after?.id,
                limit + 1,
            )
        val items = rows.take(limit).map { it.toPayslipSummary() }
        Page(items, if (rows.size > limit) "${items.last().taxMonth}:${items.last().id}" else null)
    }
}
