package dev.fajar.hris.expenses.domain.entities

data class ExpensePayableReview(
    val payable: ExpensePayable,
    val availableActions: Set<ExpensePayableAvailableAction>,
)
