package dev.fajar.hris.expenses.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import java.math.BigDecimal

fun validateExpenseDraft(input: SaveExpenseDraftCommand): Result<Unit> {
    if (
        (input.expectedVersion ?: 0) < 0 ||
            input.title.isBlank() ||
            input.title.length > 160 ||
            input.description.length > 2000 ||
            input.reason.isBlank() ||
            input.reason.length > 1000 ||
            input.lines.size > 20
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_expense_draft"))
    if (input.lines.map { it.id }.toSet().size != input.lines.size)
        return Result.Failed(Failure(FailureKind.VALIDATION, "duplicate_expense_line"))
    for ((index, line) in input.lines.withIndex()) {
        if (
            !validExpenseAmount(line.amount) ||
                line.occurredOn.year !in 1900..2200 ||
                line.description.isBlank() ||
                line.description.length > 500 ||
                line.receiptRevisionIds.size > 3 ||
                line.receiptRevisionIds.toSet().size != line.receiptRevisionIds.size
        )
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_expense_line",
                    mapOf("line" to (index + 1).toString()),
                )
            )
    }
    if (
        input.lines.fold(BigDecimal.ZERO) { total, line -> total + line.amount } >
            EXPENSE_MAXIMUM_AMOUNT
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "expense_claim_amount_limit"))
    return Result.Success(Unit)
}
