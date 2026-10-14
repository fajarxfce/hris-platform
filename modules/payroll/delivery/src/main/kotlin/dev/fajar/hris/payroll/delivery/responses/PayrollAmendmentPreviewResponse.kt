package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollAmendmentPreviewResponse(
    val firstAssessmentId: UUID,
    val sourceVersions: List<PayrollAmendmentSourceReferenceResponse>,
    val calculations: List<PayrollAmendmentCalculationResponse>,
    val difference: PayrollAmendmentDifferenceResponse,
)
