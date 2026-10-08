package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.approvals.domain.entities.ApprovalTransition

data class ExpenseReviewPlan(val review: ExpenseReview, val transition: ApprovalTransition)
