package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import java.time.LocalDate
import java.util.UUID

interface PayrollAssessmentDataSource {
    fun taxHistory(company: UUID, id: UUID): PayrollTaxAssessmentRow?

    fun payslip(company: UUID, id: UUID, allowedEmployees: Set<UUID>?): PayrollPayslipRow?

    fun payslips(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        employeeId: UUID?,
        allowedEmployees: Set<UUID>?,
        beforeMonth: LocalDate?,
        afterId: UUID?,
        limit: Int,
    ): List<PayrollPayslipSummaryRow>
}
