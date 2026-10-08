package dev.fajar.hris.expenses.data.mappers

import dev.fajar.hris.expenses.data.models.ExpenseClaimSummaryRow
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID

fun ExpenseClaimsRecord.toClaim(): ExpenseClaim =
    ExpenseClaim(
        requireNotNull(id),
        requireNotNull(employmentId),
        requireNotNull(createdBy),
        requireNotNull(createdAt).toInstant(),
        requireNotNull(version),
        requireNotNull(draftRevision),
        ExpenseClaimStatus.valueOf(requireNotNull(status)),
        requireNotNull(submissionCount),
        latestSubmissionId,
    )

fun ExpenseDraftsRecord.toDraft(
    lines: List<ExpenseDraftLinesRecord>,
    receipts: List<ExpenseDraftReceiptsRecord>,
): ExpenseDraft {
    require(lines.size == lineCount)
    val evidence = receipts.groupBy { it.lineId }
    return ExpenseDraft(
        requireNotNull(claimId),
        requireNotNull(revision),
        requireNotNull(employeeNumber),
        requireNotNull(employeeName),
        requireNotNull(title),
        requireNotNull(description),
        requireNotNull(totalAmount),
        lines.map { line ->
            ExpenseLine(
                requireNotNull(line.id),
                requireNotNull(line.categoryId),
                requireNotNull(line.occurredOn),
                requireNotNull(line.amount),
                requireNotNull(line.description),
                line.costCenterId,
                evidence[line.id].orEmpty().map { requireNotNull(it.documentRevisionId) },
            )
        },
        requireNotNull(actorId),
        requireNotNull(reason),
        requireNotNull(recordedAt).toInstant(),
    )
}

fun ExpenseClaimChangesRecord.toChange(): ExpenseClaimChange =
    ExpenseClaimChange(
        requireNotNull(claimId),
        requireNotNull(version),
        requireNotNull(draftRevision),
        ExpenseClaimStatus.valueOf(requireNotNull(status)),
        ExpenseClaimChangeKind.valueOf(requireNotNull(kind)),
        requireNotNull(actorId),
        requireNotNull(reason),
        requireNotNull(recordedAt).toInstant(),
        submissionId,
    )

fun ExpenseClaimSummaryRow.toSummary() =
    ExpenseClaimSummary(
        id,
        employmentId,
        employeeNumber,
        employeeName,
        title,
        totalAmount,
        createdAt.toInstant(),
        ExpenseClaimStatus.valueOf(status),
        version,
    )

fun ExpenseClaim.toRecord(company: UUID): ExpenseClaimsRecord =
    ExpenseClaimsRecord().also {
        it.companyId = company
        it.id = id
        it.employmentId = employmentId
        it.createdBy = createdBy
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
        it.version = version
        it.draftRevision = draftRevision
        it.status = status.name
        it.submissionCount = submissionCount
        it.latestSubmissionId = latestSubmissionId
    }

fun ExpenseDraft.toRecord(company: UUID): ExpenseDraftsRecord =
    ExpenseDraftsRecord().also {
        it.companyId = company
        it.claimId = claimId
        it.revision = revision
        it.employeeNumber = employeeNumber
        it.employeeName = employeeName
        it.title = title
        it.description = description
        it.totalAmount = totalAmount
        it.lineCount = lines.size
        it.actorId = actorId
        it.reason = reason
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
    }

fun ExpenseDraft.lineRecords(company: UUID): List<ExpenseDraftLinesRecord> =
    lines.mapIndexed { index, line ->
        ExpenseDraftLinesRecord().also {
            it.companyId = company
            it.claimId = claimId
            it.draftRevision = revision
            it.id = line.id
            it.ordinal = index + 1
            it.categoryId = line.categoryId
            it.occurredOn = line.occurredOn
            it.amount = line.amount
            it.description = line.description
            it.costCenterId = line.costCenterId
        }
    }

fun ExpenseDraft.receiptRecords(company: UUID): List<ExpenseDraftReceiptsRecord> =
    lines.flatMap { line ->
        line.receiptRevisionIds.mapIndexed { index, id ->
            ExpenseDraftReceiptsRecord().also {
                it.companyId = company
                it.claimId = claimId
                it.draftRevision = revision
                it.lineId = line.id
                it.ordinal = index + 1
                it.documentRevisionId = id
            }
        }
    }

fun ExpenseClaimChange.toRecord(company: UUID): ExpenseClaimChangesRecord =
    ExpenseClaimChangesRecord().also {
        it.companyId = company
        it.claimId = claimId
        it.version = version
        it.draftRevision = draftRevision
        it.status = status.name
        it.kind = kind.name
        it.actorId = actorId
        it.reason = reason
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        it.submissionId = submissionId
    }
