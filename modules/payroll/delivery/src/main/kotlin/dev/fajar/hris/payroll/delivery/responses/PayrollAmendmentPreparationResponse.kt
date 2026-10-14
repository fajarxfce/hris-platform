package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollAmendmentPreparationResponse(
    val firstAssessmentId: UUID,
    val sourceVersions: List<PayrollAmendmentSourceReferenceResponse>,
    val assessments: List<PayrollAmendmentBasisResponse>,
)
