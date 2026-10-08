package dev.fajar.hris.expenses.domain.entities

import java.time.Instant
import java.util.UUID

data class ExpenseClaim(
    val id: UUID,
    val employmentId: UUID,
    val createdBy: UUID,
    val createdAt: Instant,
    val version: Long,
    val draftRevision: Int,
    val status: ExpenseClaimStatus,
    val submissionCount: Int = 0,
    val latestSubmissionId: UUID? = null,
)
