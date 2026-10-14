package dev.fajar.hris.payroll.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.YearMonth

data class PayrollInputReviewResponse(
    val earningsMonth: YearMonth,
    val employee: PayrollEmployeeResponse,
    val source: PayrollWorkSourceResponse?,
    val current: PayrollInputResponse?,
    val history: Page<PayrollInputSummaryResponse>,
    val frozen: Boolean,
    val availableActions: List<String>,
)
