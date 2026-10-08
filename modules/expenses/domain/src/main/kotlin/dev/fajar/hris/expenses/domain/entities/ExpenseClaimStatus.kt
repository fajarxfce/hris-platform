package dev.fajar.hris.expenses.domain.entities

enum class ExpenseClaimStatus {
    DRAFT,
    PENDING,
    RETURNED,
    APPROVED,
    REJECTED,
    CANCELLED,
}
