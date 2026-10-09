package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.domain.entities.PayrollTaxAssessment
import java.util.UUID

interface PayrollAssessmentRepository {
    fun taxHistory(company: UUID, id: UUID): Result<PayrollTaxAssessment?>
}
