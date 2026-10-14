package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollAmendmentSourceRow
import java.util.UUID

interface PayrollAmendmentSourceDataSource {
    fun sources(company: UUID, assessmentId: UUID): List<PayrollAmendmentSourceRow>
}
