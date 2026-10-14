package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

/** Provisional calculations do not create an approval, publication or payment. */
data class PayrollAmendmentPreview(
    val firstAssessmentId: UUID,
    val sourceVersions: List<PayrollAmendmentSourceReference>,
    val calculations: List<PayrollAmendmentCalculation>,
    val difference: PayrollAmendmentDifference,
)
