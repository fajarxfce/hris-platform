package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpenseSubmissionResponse(
    val id: UUID,
    val claimId: UUID,
    val employmentId: UUID,
    val number: Int,
    val draftRevision: Int,
    val requesterId: UUID?,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val description: String,
    val totalAmount: String,
    val currency: String,
    val lines: List<ExpenseSubmittedLineResponse>,
    val makerIds: Set<UUID>,
    val submittedBy: UUID,
    val submittedAt: Instant,
    val reason: String,
    val approval: ExpenseApprovalResponse,
    val claimVersion: Long,
    val claimStatus: String,
    val current: Boolean,
    val reviews: List<ExpenseReviewResponse>,
    val duplicateReceiptDigests: Set<String>,
)
