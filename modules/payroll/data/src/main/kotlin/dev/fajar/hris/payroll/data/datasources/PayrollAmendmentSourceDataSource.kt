package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollAmendmentCandidateRow
import dev.fajar.hris.payroll.data.models.PayrollAmendmentSourceRow
import java.time.LocalDate
import java.util.UUID

interface PayrollAmendmentSourceDataSource {
    fun candidates(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        employeeId: UUID?,
        query: String,
        excludedAccountId: UUID,
        after: UUID?,
        limit: Int,
    ): List<PayrollAmendmentCandidateRow>

    fun sources(company: UUID, assessmentId: UUID): List<PayrollAmendmentSourceRow>
}
