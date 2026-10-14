package dev.fajar.hris.payroll.delivery.responses

data class PayrollAmendmentBasisResponse(
    val source: PayrollAmendmentSourceReferenceResponse,
    val employee: PayrollEmployeeResponse,
    val month: String,
    val compensation: CompensationTermsResponse,
    val input: PayrollInputTermsResponse,
    val calculation: PayrollMonthlyCalculationResponse,
)
