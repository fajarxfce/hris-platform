package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollTaxAssessmentRow
import java.util.UUID

interface PayrollAssessmentDataSource {
    fun taxHistory(company: UUID, id: UUID): PayrollTaxAssessmentRow?
}
