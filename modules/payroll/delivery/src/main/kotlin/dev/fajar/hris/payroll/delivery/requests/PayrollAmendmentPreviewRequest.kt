package dev.fajar.hris.payroll.delivery.requests

data class PayrollAmendmentPreviewRequest(
    val sourceVersions: List<PayrollAmendmentSourceReferenceRequest>,
    val corrections: List<PayrollAmendmentRemunerationRequest>,
)
