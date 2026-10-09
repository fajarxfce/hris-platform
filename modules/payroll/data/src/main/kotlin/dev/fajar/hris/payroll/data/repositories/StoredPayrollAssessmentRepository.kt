package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.data.datasources.PayrollAssessmentDataSource
import dev.fajar.hris.payroll.data.mappers.toTaxAssessment
import dev.fajar.hris.payroll.domain.entities.PayrollTaxAssessment
import dev.fajar.hris.payroll.domain.repositories.PayrollAssessmentRepository
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
}
