package dev.fajar.hris.expenses.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.people.domain.entities.EmploymentTerms
import java.math.BigDecimal
import java.time.LocalDate

fun validateExpenseSubmissionDraft(draft: ExpenseDraft, today: LocalDate): Result<Unit> {
    if (draft.lines.isEmpty())
        return Result.Failed(Failure(FailureKind.VALIDATION, "expense_lines_required"))
    if (
        draft.lines.any {
            it.occurredOn.isAfter(today) || it.occurredOn.isBefore(today.minusDays(366))
        }
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "expense_transaction_date_invalid"))
    return Result.Success(Unit)
}

fun validateExpenseLinePolicy(
    line: ExpenseLine,
    category: ExpenseCategory,
    terms: EmploymentTerms,
    today: LocalDate,
): Result<Unit> {
    if (!category.active)
        return Result.Failed(Failure(FailureKind.VALIDATION, "expense_category_unavailable"))
    if (!terms.isWorkingOn(line.occurredOn) || terms.contract !in category.policy.allowedContracts)
        return Result.Failed(Failure(FailureKind.VALIDATION, "expense_employment_ineligible"))
    if (line.occurredOn.isBefore(today.minusDays(category.policy.maximumAgeDays.toLong())))
        return Result.Failed(Failure(FailureKind.VALIDATION, "expense_transaction_expired"))
    if (line.amount > category.policy.maximumLineAmount)
        return Result.Failed(Failure(FailureKind.VALIDATION, "expense_line_limit_exceeded"))
    if (category.policy.costCenterRequired && line.costCenterId == null)
        return Result.Failed(Failure(FailureKind.VALIDATION, "expense_cost_center_required"))
    if (category.policy.receiptRequired && line.receiptRevisionIds.isEmpty())
        return Result.Failed(Failure(FailureKind.VALIDATION, "expense_receipt_required"))
    return Result.Success(Unit)
}

fun validateExpenseCategoryTotals(lines: List<ExpenseSubmittedLine>): Result<Unit> {
    val totals =
        lines
            .groupBy { it.category.id }
            .mapValues { (_, items) ->
                items.fold(BigDecimal.ZERO) { total, line -> total + line.amount }
            }
    if (
        lines.any { requireNotNull(totals[it.category.id]) > it.category.policy.maximumClaimAmount }
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "expense_category_limit_exceeded"))
    return Result.Success(Unit)
}
