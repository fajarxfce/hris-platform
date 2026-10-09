package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollRunResultResponse(
    val item: PayrollRunItemResponse,
    val facts: PayrollCalculationFactsResponse?,
    val calculation: PayrollMonthlyCalculationResponse?,
)
