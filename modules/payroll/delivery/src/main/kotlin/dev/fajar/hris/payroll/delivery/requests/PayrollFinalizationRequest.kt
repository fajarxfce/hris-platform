package dev.fajar.hris.payroll.delivery.requests

import java.util.UUID

data class PayrollFinalizationRequest(
    val id: UUID,
    val reviewId: UUID,
    val expectedRunVersion: Long,
    val expectedReviewVersion: Long,
    val expectedApprovalVersion: Long,
    val reason: String,
)
