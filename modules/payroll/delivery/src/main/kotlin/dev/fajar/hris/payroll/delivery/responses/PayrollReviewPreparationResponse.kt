package dev.fajar.hris.payroll.delivery.responses

data class PayrollReviewPreparationResponse(
    val run: PayrollRunResponse,
    val totals: PayrollRunTotalsResponse,
    val latestReview: PayrollReviewResponse?,
    val blockingIssues: List<PayrollRunFailureResponse>,
    val availableActions: List<String>,
)
