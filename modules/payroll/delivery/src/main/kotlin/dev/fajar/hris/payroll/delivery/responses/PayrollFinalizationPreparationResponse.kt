package dev.fajar.hris.payroll.delivery.responses

data class PayrollFinalizationPreparationResponse(
    val run: PayrollRunResponse,
    val totals: PayrollRunTotalsResponse,
    val latestReview: PayrollReviewResponse?,
    val approvalVersion: Long?,
    val latestFinalization: PayrollFinalizationResponse?,
    val latestJobStatus: String?,
    val blockingIssues: List<PayrollRunFailureResponse>,
    val availableActions: List<String>,
)
