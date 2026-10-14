package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Failure

data class PayrollCalculationReview(
    val period: PayrollPeriod,
    val source: PayrollWorkPeriod?,
    val policy: PayrollPolicy?,
    val runCount: Int,
    val blockingIssues: List<Failure>,
    val availableActions: Set<PayrollCalculationAction>,
)
