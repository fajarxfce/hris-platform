package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Page

data class PayrollTaxOpeningReview(
    val year: Int,
    val employee: PayrollEmployee,
    val current: PayrollTaxOpening?,
    val history: Page<PayrollTaxOpening>,
    val frozen: Boolean,
    val availableActions: Set<PayrollTaxOpeningAvailableAction>,
)
