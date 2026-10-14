package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollEmployeeRow
import java.util.UUID

interface PayrollEmployeeDataSource {
    fun employee(company: UUID, employee: UUID): PayrollEmployeeRow?

    fun employees(
        company: UUID,
        query: String,
        after: String?,
        limit: Int,
    ): List<PayrollEmployeeRow>
}
