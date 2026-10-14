package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.data.datasources.PayrollAmendmentSourceDataSource
import dev.fajar.hris.payroll.data.mappers.toAmendmentSource
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentSource
import dev.fajar.hris.payroll.domain.repositories.PayrollAmendmentSourceRepository
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredPayrollAmendmentSourceRepository(
    private val source: PayrollAmendmentSourceDataSource,
    private val json: ObjectMapper,
) : PayrollAmendmentSourceRepository {
    override fun sources(company: UUID, assessmentId: UUID): Result<List<PayrollAmendmentSource>> =
        safeDatabaseCall {
            val rows = source.sources(company, assessmentId)
            require(rows.size <= 12 && rows.all { it.companyId == company })
            require(rows.isEmpty() || rows.any { it.assessmentId == assessmentId })
            require(rows.map { it.personId }.distinct().size <= 1)
            require(rows.map { it.taxMonth.year }.distinct().size <= 1)
            require(rows.map { it.assessmentId }.distinct().size == rows.size)
            require(rows.zipWithNext().all { (before, after) -> before.taxMonth < after.taxMonth })
            java.util.List.copyOf(rows.map { it.toAmendmentSource(json) })
        }
}
