package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentCandidate
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentSource
import java.util.UUID

interface PayrollAmendmentSourceRepository {
    fun candidates(
        company: UUID,
        year: Int,
        employeeId: UUID?,
        query: String,
        excludedAccountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollAmendmentCandidate>>

    /** The complete published year for the selected assessment, in chronological order. */
    fun sources(company: UUID, assessmentId: UUID): Result<List<PayrollAmendmentSource>>
}
