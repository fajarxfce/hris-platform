package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollPaymentInstructionRequest.toInstruction() =
    PayrollPaymentInstruction(
        id,
        assessmentId,
        PayrollPaymentDestination(
            destination.bankCode,
            destination.accountNumber,
            destination.accountName,
        ),
    )

fun PayrollPaymentResultRequest.toResult() =
    PayrollPaymentResult(
        itemId,
        status,
        transactionReference,
        occurredAt,
        reason,
        confirmedNoTransfer,
    )

fun PayrollPayable.toResponse() =
    PayrollPayableResponse(
        assessmentId,
        employmentId,
        employeeNumber,
        employeeName,
        taxMonth,
        plannedPaymentDate,
        amount.toPlainString(),
        "IDR",
        publishedAt,
    )

fun PayrollPaymentBatch.toResponse() =
    PayrollPaymentBatchResponse(
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

fun PayrollPaymentItem.toResponse() =
    PayrollPaymentItemResponse(
        id,
        assessmentId,
        employmentId,
        employeeNumber,
        employeeName,
        taxMonth,
        plannedPaymentDate,
        amount.toPlainString(),
        "IDR",
        PayrollPaymentDestinationResponse(
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

fun PayrollPaymentSummary.toResponse() =
    PayrollPaymentSummaryResponse(
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

fun PayrollPaymentAction.toResponse() =
    PayrollPaymentActionResponse(version, kind.name, status.name, actorId, reason, recordedAt)

fun PayrollPaymentReconciliation.toResponse() =
    PayrollPaymentReconciliationResponse(
        batchVersion,
        actorId,
        recordedAt,
        resultingStatus.name,
        results.map {
            PayrollPaymentResultResponse(
                it.itemId,
                it.status.name,
                it.transactionReference,
                it.occurredAt,
                it.reason,
            )
        },
    )

fun PayrollPaymentProgress.toResponse() =
    PayrollPaymentProgressResponse(
        assessmentId,
        version,
        attempts.map {
            PayrollPaymentAttemptResponse(
                it.batchId,
                it.itemId,
                it.status.name,
                it.createdAt,
                it.releasedAt,
                it.resolvedAt,
            )
        },
    )
