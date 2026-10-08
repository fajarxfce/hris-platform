package dev.fajar.hris.expenses.data.mappers

import dev.fajar.hris.expenses.data.models.ExpenseCategoryRow
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.people.domain.entities.ContractKind

fun ExpenseCategoryRow.toCategory(): ExpenseCategory =
    ExpenseCategory(
        requireNotNull(revision.categoryId),
        code,
        version,
        requireNotNull(revision.revision),
        requireNotNull(revision.effectiveFrom),
        ExpensePolicy(
            requireNotNull(revision.name),
            requireNotNull(revision.maximumLineAmount),
            requireNotNull(revision.maximumClaimAmount),
            requireNotNull(revision.receiptRequired),
            requireNotNull(revision.costCenterRequired),
            requireNotNull(revision.maximumAgeDays),
            requireNotNull(revision.allowedContracts).map { ContractKind.valueOf(it) }.toSet(),
        ),
        requireNotNull(revision.active),
    )

fun ExpenseCategoryRow.toHistory(): ExpenseCategoryRevision =
    ExpenseCategoryRevision(
        toCategory(),
        requireNotNull(revision.actorId),
        requireNotNull(revision.reason),
        requireNotNull(revision.recordedAt).toInstant(),
    )
