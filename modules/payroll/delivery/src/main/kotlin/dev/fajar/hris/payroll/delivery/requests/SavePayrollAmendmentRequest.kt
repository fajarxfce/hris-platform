package dev.fajar.hris.payroll.delivery.requests

import java.util.UUID

data class SavePayrollAmendmentRequest(
    val firstAssessmentId: UUID,
    val expectedVersion: Long? = null,
    val sourceVersions: List<PayrollAmendmentSourceReferenceRequest>,
    val corrections: List<PayrollAmendmentRemunerationRequest>,
    val reason: String,
)
