package dev.fajar.hris.payroll.delivery.requests

import java.util.UUID

data class PayrollReviewSubmitRequest(
    val id: UUID,
    val expectedRunVersion: Long,
    val reason: String,
)
