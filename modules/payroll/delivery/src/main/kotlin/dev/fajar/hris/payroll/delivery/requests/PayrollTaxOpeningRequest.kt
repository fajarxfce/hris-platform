package dev.fajar.hris.payroll.delivery.requests
data class PayrollTaxOpeningRequest(
    val terms: PayrollTaxOpeningTermsRequest,
    val expectedVersion: Long? = null,
    val expectedEmploymentVersion: Long,
    val reason: String,
)
