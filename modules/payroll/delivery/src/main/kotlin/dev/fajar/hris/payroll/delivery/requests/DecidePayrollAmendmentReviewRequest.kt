package dev.fajar.hris.payroll.delivery.requests

import dev.fajar.hris.approvals.domain.entities.ApprovalDecision
import io.swagger.v3.oas.annotations.media.Schema

data class DecidePayrollAmendmentReviewRequest(
    @field:Schema(minimum = "0", maximum = "999") val expectedVersion: Long,
    @field:Schema(minimum = "0", maximum = "9") val expectedReviewVersion: Long,
    @field:Schema(minimum = "0") val expectedApprovalVersion: Long,
    val decision: ApprovalDecision,
    @field:Schema(minLength = 1, maxLength = 1000) val reason: String,
)
