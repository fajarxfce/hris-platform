package dev.fajar.hris.payroll.delivery.requests

import java.util.UUID

data class StartPayrollAmendmentPublicationRequest(
    val expectedVersion: Long,
    val reviewId: UUID,
    val expectedReviewVersion: Long,
    val expectedApprovalVersion: Long,
    val calculationId: UUID,
    val reason: String,
)
