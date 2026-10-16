package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollAmendmentCalculationResponse(
    val source: PayrollAmendmentSourceReferenceResponse,
    val employee: PayrollEmployeeResponse,
    val month: String,
    val compensation: CompensationTermsResponse,
    val input: PayrollInputTermsResponse,
    val original: PayrollMonthlyCalculationResponse,
    val calculation: PayrollMonthlyCalculationResponse,
    val difference: PayrollAmendmentDifferenceResponse,
    val workAdjustmentId: UUID?,
    val workDays: List<PayrollWorkDayResponse>,
)
