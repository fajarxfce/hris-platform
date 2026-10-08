package dev.fajar.hris.expenses.data.mappers

import dev.fajar.hris.expenses.data.models.ExpensePaymentSummaryRow
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID

fun ExpensePayableCandidatesRecord.toPayable() =
    ExpensePayable(
        requireNotNull(claimId),
        requireNotNull(submissionId),
        requireNotNull(employmentId),
        requireNotNull(employeeNumber),
        requireNotNull(employeeName),
        requireNotNull(title),
        requireNotNull(amount),
        requireNotNull(approvedAt).toInstant(),
        requireNotNull(makerIds).filterNotNull().toSet(),
        requesterId,
        currentAccountId,
    )

fun ExpensePaymentItemsRecord.toItem() =
    ExpensePaymentItem(
        requireNotNull(id),
        requireNotNull(claimId),
        requireNotNull(submissionId),
        requireNotNull(employmentId),
        requireNotNull(employeeNumber),
        requireNotNull(employeeName),
        requireNotNull(amount),
        ExpensePaymentDestination(
            requireNotNull(bankCode),
            requireNotNull(accountNumber),
            requireNotNull(accountName),
        ),
        ExpensePaymentItemStatus.valueOf(requireNotNull(status)),
        requireNotNull(version),
        requireNotNull(batchVersion),
        transactionReference,
        occurredAt?.toInstant(),
    )

fun ExpensePaymentBatchesRecord.toBatch(items: List<ExpensePaymentItemsRecord>) =
    ExpensePaymentBatch(
        requireNotNull(id),
        ExpensePaymentBatchStatus.valueOf(requireNotNull(status)),
        requireNotNull(version),
        requireNotNull(title),
        requireNotNull(totalAmount),
        requireNotNull(itemCount),
        requireNotNull(createdBy),
        requireNotNull(createdAt).toInstant(),
        releasedBy,
        releasedAt?.toInstant(),
        items.map { it.toItem() },
    )

fun ExpensePaymentSummaryRow.toSummary() =
    ExpensePaymentSummary(
        requireNotNull(batch.id),
        ExpensePaymentBatchStatus.valueOf(requireNotNull(batch.status)),
        requireNotNull(batch.version),
        requireNotNull(batch.title),
        requireNotNull(batch.totalAmount),
        requireNotNull(batch.itemCount),
        pending,
        succeeded,
        failed,
        requireNotNull(batch.createdBy),
        requireNotNull(batch.createdAt).toInstant(),
    )

fun ExpensePaymentActionsRecord.toAction() =
    ExpensePaymentAction(
        requireNotNull(batchId),
        requireNotNull(version),
        ExpensePaymentActionKind.valueOf(requireNotNull(kind)),
        ExpensePaymentBatchStatus.valueOf(requireNotNull(status)),
        requireNotNull(actorId),
        requireNotNull(reason),
        requireNotNull(recordedAt).toInstant(),
    )

fun ExpensePaymentResultsRecord.toResult() =
    ExpensePaymentResult(
        requireNotNull(itemId),
        ExpensePaymentItemStatus.valueOf(requireNotNull(status)),
        transactionReference,
        requireNotNull(occurredAt).toInstant(),
        requireNotNull(reason),
        status == "FAILED",
    )

fun ExpensePaymentBatch.toRecord(company: UUID) =
    ExpensePaymentBatchesRecord().also {
        it.companyId = company
        it.id = id
        it.status = status.name
        it.version = version
        it.title = title
        it.totalAmount = totalAmount
        it.itemCount = itemCount
        it.createdBy = createdBy
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
        it.releasedBy = releasedBy
        it.releasedAt = releasedAt?.atOffset(ZoneOffset.UTC)
    }

fun ExpensePaymentItem.toRecord(company: UUID, batch: UUID) =
    ExpensePaymentItemsRecord().also {
        it.companyId = company
        it.id = id
        it.batchId = batch
        it.claimId = claimId
        it.submissionId = submissionId
        it.employmentId = employmentId
        it.employeeNumber = employeeNumber
        it.employeeName = employeeName
        it.amount = amount
        it.bankCode = destination.bankCode
        it.accountNumber = destination.accountNumber
        it.accountName = destination.accountName
        it.status = status.name
        it.version = version
        it.batchVersion = batchVersion
        it.transactionReference = transactionReference
        it.occurredAt = occurredAt?.atOffset(ZoneOffset.UTC)
    }

fun ExpensePaymentAction.toRecord(company: UUID) =
    ExpensePaymentActionsRecord().also {
        it.companyId = company
        it.batchId = batchId
        it.version = version
        it.kind = kind.name
        it.status = status.name
        it.actorId = actorId
        it.reason = reason
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
    }

fun ExpensePaymentReconciliation.toRecords(company: UUID) =
    results.map { result ->
        ExpensePaymentResultsRecord().also {
            it.companyId = company
            it.batchId = batchId
            it.itemId = result.itemId
            it.batchVersion = batchVersion
            it.status = result.status.name
            it.transactionReference = result.transactionReference
            it.occurredAt = result.occurredAt.atOffset(ZoneOffset.UTC)
            it.actorId = actorId
            it.reason = result.reason
            it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        }
    }
