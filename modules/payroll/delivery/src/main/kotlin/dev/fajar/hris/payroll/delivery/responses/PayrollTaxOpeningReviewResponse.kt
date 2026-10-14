package dev.fajar.hris.payroll.delivery.responses

import dev.fajar.hris.core.domain.Page

data class PayrollTaxOpeningReviewResponse(
    val year: Int,
    val employee: PayrollEmployeeResponse,
    val current: PayrollTaxOpeningResponse?,
    val history: Page<PayrollTaxOpeningSummaryResponse>,
    val frozen: Boolean,
    val availableActions: List<String>,
)
