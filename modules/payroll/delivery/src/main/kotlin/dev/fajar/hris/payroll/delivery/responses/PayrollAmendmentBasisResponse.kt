package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollAmendmentBasisResponse(
    val source: PayrollAmendmentSourceReferenceResponse,
    val employee: PayrollEmployeeResponse,
    val month: String,
    val compensation: CompensationTermsResponse,
    val input: PayrollInputTermsResponse,
    val calculation: PayrollMonthlyCalculationResponse,
    val workAdjustmentId: UUID?,
    val availableWorkAdjustment: PayrollWorkAdjustmentResponse?,
    val workDays: List<PayrollWorkDayResponse>,
)
