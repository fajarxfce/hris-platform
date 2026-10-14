package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Failure

data class PayrollReviewPreparation(
    val run: PayrollRun,
    val totals: PayrollRunTotals,
    val latestReview: PayrollReview?,
    val blockingIssues: List<Failure>,
    val availableActions: Set<PayrollReviewAvailableAction>,
)
