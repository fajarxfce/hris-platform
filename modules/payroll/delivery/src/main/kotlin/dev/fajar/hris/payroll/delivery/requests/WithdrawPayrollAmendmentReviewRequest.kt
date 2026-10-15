package dev.fajar.hris.payroll.delivery.requests

import io.swagger.v3.oas.annotations.media.Schema

data class WithdrawPayrollAmendmentReviewRequest(
    @field:Schema(minimum = "0", maximum = "999") val expectedVersion: Long,
    @field:Schema(minimum = "0", maximum = "9") val expectedReviewVersion: Long,
    @field:Schema(minimum = "0") val expectedApprovalVersion: Long,
    @field:Schema(minLength = 1, maxLength = 1000) val reason: String,
)
