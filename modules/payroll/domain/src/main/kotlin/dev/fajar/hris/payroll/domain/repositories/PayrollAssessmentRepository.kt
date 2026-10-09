package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.YearMonth
import java.util.UUID

interface PayrollAssessmentRepository {
    fun taxHistory(company: UUID, id: UUID): Result<PayrollTaxAssessment?>

    fun payslip(company: UUID, id: UUID, allowedEmployees: Set<UUID>?): Result<PayrollPayslip?>

    fun payslips(
        company: UUID,
        from: YearMonth,
        until: YearMonth,
        employeeId: UUID?,
        allowedEmployees: Set<UUID>?,
        after: PayrollPayslipCursor?,
        limit: Int,
    ): Result<Page<PayrollPayslipSummary>>
}
