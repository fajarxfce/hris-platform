package dev.fajar.hris.expenses.data.mappers

import dev.fajar.hris.expenses.data.models.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID

fun ExpenseSubmissionsRecord.toSubmission(
    draft: ExpenseDraftsRecord,
    lines: List<ExpenseSubmittedLineRow>,
    receipts: List<ExpenseSubmittedReceiptsRecord>,
): ExpenseSubmission {
    require(lines.size == draft.lineCount && lines.isNotEmpty())
    val byLine = receipts.groupBy { it.lineId }
    return ExpenseSubmission(
        requireNotNull(id),
        requireNotNull(claimId),
        requireNotNull(number),
        requireNotNull(draftRevision),
        requireNotNull(approvalId),
        requesterId,
        requireNotNull(employeeNumber),
        requireNotNull(employeeName),
        requireNotNull(draft.title),
        requireNotNull(draft.description),
        requireNotNull(draft.totalAmount),
        lines.map { row ->
            val line = row.line
            val frozen = row.snapshot
            ExpenseSubmittedLine(
                requireNotNull(line.id),
                requireNotNull(line.occurredOn),
                requireNotNull(line.amount),
                requireNotNull(line.description),
                ExpenseCategoryRow(
                        row.categoryCode,
                        requireNotNull(frozen.categoryVersion),
                        row.category,
                    )
                    .toCategory(),
                line.costCenterId?.let {
                    ExpenseCostCenterSnapshot(
                        it,
                        requireNotNull(frozen.costCenterCode),
                        requireNotNull(frozen.costCenterName),
                        requireNotNull(frozen.costCenterVersion),
                    )
                },
                byLine[line.id].orEmpty().map {
                    ExpenseSubmittedReceipt(
                        requireNotNull(it.documentRevisionId),
                        requireNotNull(it.fileName),
                        requireNotNull(it.mediaType),
                        requireNotNull(it.size),
                        requireNotNull(it.sha256),
                        requireNotNull(it.possibleDuplicate),
                    )
                },
            )
        },
        requireNotNull(makerIds).toSet(),
        requireNotNull(submittedBy),
        requireNotNull(submittedAt).toInstant(),
        requireNotNull(reason),
    )
}

fun ExpenseSubmissionSummaryRow.toSubmissionSummary() =
    ExpenseSubmissionSummary(
        id,
        number,
        draftRevision,
        approvalId,
        title,
        totalAmount,
        submittedBy,
        submittedAt.toInstant(),
    )

fun ExpenseSubmission.toRecord(company: UUID): ExpenseSubmissionsRecord =
    ExpenseSubmissionsRecord().also {
        it.companyId = company
        it.claimId = claimId
        it.id = id
        it.number = number
        it.draftRevision = draftRevision
        it.approvalId = approvalId
        it.requesterId = requesterId
        it.employeeNumber = employeeNumber
        it.employeeName = employeeName
        it.makerIds = makerIds.sorted().toTypedArray()
        it.submittedBy = submittedBy
        it.submittedAt = submittedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
    }

fun ExpenseSubmission.submittedLineRecords(company: UUID): List<ExpenseSubmittedLinesRecord> =
    lines.map { line ->
        ExpenseSubmittedLinesRecord().also {
            it.companyId = company
            it.submissionId = id
            it.claimId = claimId
            it.draftRevision = draftRevision
            it.lineId = line.id
            it.categoryId = line.category.id
            it.categoryRevision = line.category.appliedRevision
            it.categoryVersion = line.category.version
            it.costCenterCode = line.costCenter?.code
            it.costCenterName = line.costCenter?.name
            it.costCenterVersion = line.costCenter?.version
        }
    }

fun ExpenseSubmission.submittedReceiptRecords(company: UUID): List<ExpenseSubmittedReceiptsRecord> =
    lines.flatMap { line ->
        line.receipts.mapIndexed { index, receipt ->
            ExpenseSubmittedReceiptsRecord().also {
                it.companyId = company
                it.submissionId = id
                it.claimId = claimId
                it.draftRevision = draftRevision
                it.lineId = line.id
                it.ordinal = index + 1
                it.documentRevisionId = receipt.revisionId
                it.sha256 = receipt.sha256
                it.fileName = receipt.fileName
                it.mediaType = receipt.mediaType
                it.size = receipt.size
                it.possibleDuplicate = receipt.possibleDuplicate
            }
        }
    }
