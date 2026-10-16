package dev.fajar.hris.payroll.delivery.requests

import java.util.UUID

data class PayrollInputRequest(
    val workJobId: UUID,
    val expectedWorkPeriodVersion: Long,
    val expectedEmploymentVersion: Long,
    val terms: PayrollInputTermsRequest,
    val expectedVersion: Long? = null,
    val reason: String,
    val workAdjustmentId: UUID? = null,
)
