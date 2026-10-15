package dev.fajar.hris.payroll.delivery.responses

data class PayrollAmendmentReviewDetailsResponse(
    val review: PayrollAmendmentReviewResponse,
    val amendment: PayrollAmendmentSummaryResponse,
    val calculation: PayrollAmendmentCalculationSummaryResponse,
    val approval: PayrollReviewApprovalResponse,
    val history: List<PayrollAmendmentReviewChangeResponse>,
    val accounts: List<PayrollReviewAccountResponse>,
    val availableActions: Set<String>,
)
