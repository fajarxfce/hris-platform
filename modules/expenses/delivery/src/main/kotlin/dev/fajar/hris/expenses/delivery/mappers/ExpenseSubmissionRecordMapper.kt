package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.*

fun ExpenseSubmissionRecord.toResponse(claim: ExpenseClaim): ExpenseSubmissionRecordResponse {
    val s = submission
    return ExpenseSubmissionRecordResponse(
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
        reviews.map { it.toResponse() },
    )
}
