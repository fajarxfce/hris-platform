package dev.fajar.hris.payroll.delivery.responses

data class PayrollCalculationReviewResponse(
    val period: PayrollPeriodResponse,
    val source: PayrollCalculationSourceResponse?,
    val policy: PayrollCalculationPolicyResponse?,
    val runCount: Int,
    val blockingIssues: List<PayrollRunFailureResponse>,
    val availableActions: List<String>,
)
