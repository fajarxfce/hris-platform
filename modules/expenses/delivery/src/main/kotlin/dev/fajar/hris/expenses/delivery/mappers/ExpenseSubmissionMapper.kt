package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.*

fun ExpenseSubmissionDetails.toResponse(): ExpenseSubmissionResponse {
    val s = submission
    return ExpenseSubmissionResponse(
        s.id,
        s.claimId,
        claim.employmentId,
        s.number,
        s.draftRevision,
        s.requesterId,
        s.employeeNumber,
        s.employeeName,
        s.title,
        s.description,
        s.totalAmount.toPlainString(),
        "IDR",
        s.lines.map { it.toResponse() },
        s.makerIds,
        s.submittedBy,
        s.submittedAt,
        s.reason,
        ExpenseApprovalResponse(
            approval.id,
            approval.status.name,
            approval.version,
            approval.currentStep,
            approval.templateId,
            approval.templateRevision,
            approval.stages.map { it.assignees },
        ),
        claim.version,
        claim.status.name,
        claim.latestSubmissionId == s.id,
    )
}

fun ExpenseSubmittedLine.toResponse() =
    ExpenseSubmittedLineResponse(
        id,
        occurredOn,
        amount.toPlainString(),
        description,
        category.toResponse(),
        costCenter?.let { ExpenseCostCenterSnapshotResponse(it.id, it.code, it.name, it.version) },
        receipts.map { it.toResponse() },
    )

fun ExpenseSubmittedReceipt.toResponse() =
    ExpenseSubmittedReceiptResponse(
        revisionId,
        fileName,
        mediaType,
        size,
        sha256,
        possibleDuplicate,
    )

fun ExpenseSubmissionSummary.toResponse() =
    ExpenseSubmissionSummaryResponse(
        id,
        number,
        draftRevision,
        approvalId,
        title,
        totalAmount.toPlainString(),
        "IDR",
        submittedBy,
        submittedAt,
    )
