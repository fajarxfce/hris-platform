package dev.fajar.hris.payroll.delivery.responses

data class PayrollAmendmentCalculationResponse(
    val source: PayrollAmendmentSourceReferenceResponse,
    val employee: PayrollEmployeeResponse,
    val month: String,
    val compensation: CompensationTermsResponse,
    val input: PayrollInputTermsResponse,
    val original: PayrollMonthlyCalculationResponse,
    val calculation: PayrollMonthlyCalculationResponse,
    val difference: PayrollAmendmentDifferenceResponse,
)
