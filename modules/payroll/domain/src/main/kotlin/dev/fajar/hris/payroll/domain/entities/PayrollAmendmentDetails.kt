package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Page

data class PayrollAmendmentDetails(
    val amendment: PayrollAmendment,
    val history: Page<PayrollAmendmentSummary>,
    val latestCalculation: PayrollAmendmentCalculationSummary?,
    val availableActions: Set<PayrollAmendmentAvailableAction>,
    val latestReview: PayrollAmendmentReview? = null,
)
