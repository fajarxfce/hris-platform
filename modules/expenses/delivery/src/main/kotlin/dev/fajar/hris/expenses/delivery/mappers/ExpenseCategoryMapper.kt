package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.core.http.decimalAmount
import dev.fajar.hris.expenses.delivery.requests.ExpenseCategoryRequest
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.*
import java.util.UUID

fun ExpenseCategoryRequest.toCategory(id: UUID) =
    ExpenseCategory(
        id,
        code,
        expectedVersion ?: 0,
        expectedVersion ?: 0,
        effectiveFrom,
        ExpensePolicy(
            name,
            decimalAmount(maximumLineAmount),
            decimalAmount(maximumClaimAmount),
            receiptRequired,
            costCenterRequired,
            maximumAgeDays,
            allowedContracts,
        ),
        active,
    )

fun ExpenseCategory.toResponse() =
    ExpenseCategoryResponse(
        id,
        code,
        policy.name,
        "IDR",
        effectiveFrom,
        policy.maximumLineAmount.toPlainString(),
        policy.maximumClaimAmount.toPlainString(),
        policy.receiptRequired,
        policy.costCenterRequired,
        policy.maximumAgeDays,
        policy.allowedContracts.map { it.name }.toSet(),
        active,
        version,
        appliedRevision,
    )

fun ExpenseCategoryRevision.toResponse() =
    ExpenseCategoryRevisionResponse(category.toResponse(), actorId, reason, recordedAt)
