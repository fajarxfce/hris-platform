package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.YearMonth

data class PayrollInputReview(
    val earningsMonth: YearMonth,
    val employee: PayrollEmployee,
    val source: PayrollWorkSource?,
    val current: PayrollInput?,
    val history: Page<PayrollInputSummary>,
    val frozen: Boolean,
    val availableActions: Set<PayrollInputAvailableAction>,
)
