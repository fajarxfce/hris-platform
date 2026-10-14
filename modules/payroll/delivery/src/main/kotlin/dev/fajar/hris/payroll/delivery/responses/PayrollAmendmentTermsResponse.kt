package dev.fajar.hris.payroll.delivery.responses

data class PayrollAmendmentTermsResponse(
    val sourceVersions: List<PayrollAmendmentSourceReferenceResponse>,
    val corrections: List<PayrollAmendmentRemunerationResponse>,
)
