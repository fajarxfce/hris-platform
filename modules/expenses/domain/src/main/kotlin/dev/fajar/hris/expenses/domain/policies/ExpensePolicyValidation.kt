package dev.fajar.hris.expenses.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseCategory
import java.math.BigDecimal

val EXPENSE_MAXIMUM_AMOUNT: BigDecimal = BigDecimal("999999999999.99")

fun validExpenseAmount(amount: BigDecimal): Boolean =
    amount.signum() > 0 && amount.scale() in 0..2 && amount <= EXPENSE_MAXIMUM_AMOUNT

fun validateExpenseCategory(category: ExpenseCategory, reason: String): Result<Unit> {
    val policy = category.policy
    if (
        !category.code.matches(Regex("[A-Z][A-Z0-9_]{1,31}")) ||
            category.effectiveFrom.year !in 1900..2200 ||
            policy.name.isBlank() ||
            policy.name.length > 120 ||
            !validExpenseAmount(policy.maximumLineAmount) ||
            !validExpenseAmount(policy.maximumClaimAmount) ||
            policy.maximumClaimAmount < policy.maximumLineAmount ||
            policy.maximumAgeDays !in 1..366 ||
            policy.allowedContracts.isEmpty() ||
            reason.isBlank() ||
            reason.length > 1000
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_expense_policy"))
    return Result.Success(Unit)
}
