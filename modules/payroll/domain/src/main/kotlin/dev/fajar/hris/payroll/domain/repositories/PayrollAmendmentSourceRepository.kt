package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentSource
import java.util.UUID

interface PayrollAmendmentSourceRepository {
    /** The complete published year for the selected assessment, in chronological order. */
    fun sources(company: UUID, assessmentId: UUID): Result<List<PayrollAmendmentSource>>
}
