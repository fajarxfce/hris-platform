package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.core.http.decimalAmount
import dev.fajar.hris.expenses.delivery.requests.*
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.*
import java.util.UUID

fun SaveExpenseDraftRequest.toCommand(id: UUID) =
    SaveExpenseDraftCommand(
        id,
        employmentId,
        expectedVersion,
        title,
        description,
        lines.map { it.toLine() },
        reason,
    )

fun ExpenseLineRequest.toLine() =
    ExpenseLine(
        id,
        categoryId,
        occurredOn,
        decimalAmount(amount),
        description,
        costCenterId,
        receiptRevisionIds.toList(),
    )

fun ExpenseLine.toResponse() =
    ExpenseLineResponse(
        id,
        categoryId,
        occurredOn,
        amount.toPlainString(),
        description,
        costCenterId,
        receiptRevisionIds,
    )

fun ExpenseDraft.toResponse() =
    ExpenseDraftResponse(
        revision,
        employeeNumber,
        employeeName,
        title,
        description,
        totalAmount.toPlainString(),
        "IDR",
        lines.map { it.toResponse() },
        actorId,
        reason,
        recordedAt,
    )

fun ExpenseClaimDetails.toResponse() =
    ExpenseClaimResponse(
        claim.id,
        claim.employmentId,
        claim.createdBy,
        claim.createdAt,
        claim.version,
        claim.draftRevision,
        claim.status.name,
        draft.toResponse(),
    )

fun ExpenseClaimSummary.toResponse() =
    ExpenseClaimSummaryResponse(
        id,
        employmentId,
        employeeNumber,
        employeeName,
        title,
        totalAmount.toPlainString(),
        "IDR",
        createdAt,
        status.name,
        version,
    )

fun ExpenseClaimChange.toResponse() =
    ExpenseClaimChangeResponse(
        version,
        draftRevision,
        status.name,
        kind.name,
        actorId,
        reason,
        recordedAt,
    )
