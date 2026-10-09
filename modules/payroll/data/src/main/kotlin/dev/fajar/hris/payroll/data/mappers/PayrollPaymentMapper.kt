package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.PayrollPaymentSummaryRow
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

fun PayrollPayableCandidatesRecord.toPayable() =
    PayrollPayable(
        requireNotNull(assessmentId),
        requireNotNull(employmentId),
        requireNotNull(employeeNumber),
        requireNotNull(employeeName),
        YearMonth.from(requireNotNull(taxMonth)),
        requireNotNull(plannedPaymentDate),
        requireNotNull(amount),
        requireNotNull(publishedAt).toInstant(),
    )

fun PayrollPaymentItemsRecord.toItem() =
    PayrollPaymentItem(
        requireNotNull(id),
        requireNotNull(assessmentId),
        requireNotNull(employmentId),
        requireNotNull(employeeNumber),
        requireNotNull(employeeName),
        YearMonth.from(requireNotNull(taxMonth)),
        requireNotNull(plannedPaymentDate),
        requireNotNull(amount),
        PayrollPaymentDestination(
            requireNotNull(bankCode),
            requireNotNull(accountNumber),
            requireNotNull(accountName),
        ),
        PayrollPaymentItemStatus.valueOf(requireNotNull(status)),
        requireNotNull(version),
        requireNotNull(batchVersion),
        transactionReference,
        occurredAt?.toInstant(),
    )

fun PayrollPaymentBatchesRecord.toBatch(items: List<PayrollPaymentItemsRecord>) =
    PayrollPaymentBatch(
        requireNotNull(id),
        PayrollPaymentBatchStatus.valueOf(requireNotNull(status)),
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

fun PayrollPaymentSummaryRow.toSummary() =
    PayrollPaymentSummary(
        requireNotNull(batch.id),
        PayrollPaymentBatchStatus.valueOf(requireNotNull(batch.status)),
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

fun PayrollPaymentActionsRecord.toAction() =
    PayrollPaymentAction(
        requireNotNull(batchId),
        requireNotNull(version),
        PayrollPaymentActionKind.valueOf(requireNotNull(kind)),
        PayrollPaymentBatchStatus.valueOf(requireNotNull(status)),
        requireNotNull(actorId),
        requireNotNull(reason),
        requireNotNull(recordedAt).toInstant(),
    )

fun PayrollPaymentResultsRecord.toResult() =
    PayrollPaymentResult(
        requireNotNull(itemId),
        PayrollPaymentItemStatus.valueOf(requireNotNull(status)),
        transactionReference,
        requireNotNull(occurredAt).toInstant(),
        requireNotNull(reason),
        status == "FAILED",
    )

fun PayrollPaymentBatch.toRecord(company: UUID) =
    PayrollPaymentBatchesRecord().also {
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

fun PayrollPaymentItem.toRecord(company: UUID, batch: UUID) =
    PayrollPaymentItemsRecord().also {
        it.companyId = company
        it.id = id
        it.batchId = batch
        it.assessmentId = assessmentId
        it.employmentId = employmentId
        it.employeeNumber = employeeNumber
        it.employeeName = employeeName
        it.taxMonth = taxMonth.atDay(1)
        it.plannedPaymentDate = plannedPaymentDate
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

fun PayrollPaymentAction.toRecord(company: UUID) =
    PayrollPaymentActionsRecord().also {
        it.companyId = company
        it.batchId = batchId
        it.version = version
        it.kind = kind.name
        it.status = status.name
        it.actorId = actorId
        it.reason = reason
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
    }

fun PayrollPaymentReconciliation.toRecords(company: UUID) =
    results.map { result ->
        PayrollPaymentResultsRecord().also {
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
