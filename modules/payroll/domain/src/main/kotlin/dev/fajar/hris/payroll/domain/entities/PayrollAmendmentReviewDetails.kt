package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.approvals.domain.entities.ApprovalRequest

data class PayrollAmendmentReviewDetails(
    val review: PayrollAmendmentReview,
    val amendment: PayrollAmendmentSummary,
    val calculation: PayrollAmendmentCalculationSummary,
    val approval: ApprovalRequest,
    val history: List<PayrollAmendmentReviewChange>,
    val accounts: List<PayrollReviewAccount>,
    val availableActions: Set<PayrollAmendmentReviewAvailableAction>,
)
