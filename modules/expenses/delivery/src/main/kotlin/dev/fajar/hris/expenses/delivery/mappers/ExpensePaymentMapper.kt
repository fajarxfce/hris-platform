package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.expenses.delivery.requests.*
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.*

fun ExpensePaymentInstructionRequest.toInstruction() =
    ExpensePaymentInstruction(
        id,
        submissionId,
        ExpensePaymentDestination(
            destination.bankCode,
            destination.accountNumber,
            destination.accountName,
        ),
    )

fun ExpensePaymentResultRequest.toResult() =
    ExpensePaymentResult(
        itemId,
        status,
        transactionReference,
        occurredAt,
        reason,
        confirmedNoTransfer,
    )

fun ExpensePayable.toResponse() =
    ExpensePayableResponse(
        claimId,
        submissionId,
        employmentId,
        employeeNumber,
        employeeName,
        title,
        amount.toPlainString(),
        "IDR",
        approvedAt,
    )

fun ExpensePaymentBatch.toResponse() =
    ExpensePaymentBatchResponse(
        id,
        status.name,
        version,
        title,
        totalAmount.toPlainString(),
        "IDR",
        itemCount,
        createdBy,
        createdAt,
        releasedBy,
        releasedAt,
        items.map { it.toResponse() },
    )

fun ExpensePaymentItem.toResponse() =
    ExpensePaymentItemResponse(
        id,
        claimId,
        submissionId,
        employmentId,
        employeeNumber,
        employeeName,
        amount.toPlainString(),
        "IDR",
        ExpensePaymentDestinationResponse(
            destination.bankCode,
            destination.accountNumber,
            destination.accountName,
        ),
        status.name,
        version,
        batchVersion,
        transactionReference,
        occurredAt,
    )

fun ExpensePaymentSummary.toResponse() =
    ExpensePaymentSummaryResponse(
        id,
        status.name,
        version,
        title,
        totalAmount.toPlainString(),
        "IDR",
        itemCount,
        pendingCount,
        succeededCount,
        failedCount,
        createdBy,
        createdAt,
    )

fun ExpensePaymentAction.toResponse() =
    ExpensePaymentActionResponse(version, kind.name, status.name, actorId, reason, recordedAt)

fun ExpensePaymentReconciliation.toResponse() =
    ExpensePaymentReconciliationResponse(
        batchVersion,
        actorId,
        recordedAt,
        resultingStatus.name,
        results.map {
            ExpensePaymentResultResponse(
                it.itemId,
                it.status.name,
                it.transactionReference,
                it.occurredAt,
                it.reason,
            )
        },
    )
