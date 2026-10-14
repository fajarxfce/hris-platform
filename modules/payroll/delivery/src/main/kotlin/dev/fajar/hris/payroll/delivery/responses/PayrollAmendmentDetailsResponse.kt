package dev.fajar.hris.payroll.delivery.responses

import dev.fajar.hris.core.domain.Page

data class PayrollAmendmentDetailsResponse(
    val amendment: PayrollAmendmentResponse,
    val history: Page<PayrollAmendmentSummaryResponse>,
    val latestCalculation: PayrollAmendmentCalculationSummaryResponse?,
    val availableActions: Set<String>,
)
