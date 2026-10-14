package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.PayrollAmendmentCalculationsRecord
import java.util.UUID

data class PayrollAmendmentCalculationRow(
    val summary: PayrollAmendmentCalculationsRecord,
    val firstAssessmentId: UUID,
    val sourceVersions: String,
)
