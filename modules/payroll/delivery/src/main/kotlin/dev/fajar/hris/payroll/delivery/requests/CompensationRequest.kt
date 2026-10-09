package dev.fajar.hris.payroll.delivery.requests

import java.time.YearMonth

data class CompensationRequest(
    val effectiveFrom: YearMonth,
    val terms: CompensationTermsRequest,
    val expectedVersion: Long? = null,
    val expectedEmploymentVersion: Long,
    val reason: String,
)
