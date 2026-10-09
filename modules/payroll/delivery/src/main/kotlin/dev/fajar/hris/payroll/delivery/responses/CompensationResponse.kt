package dev.fajar.hris.payroll.delivery.responses

import java.time.YearMonth
import java.util.UUID

data class CompensationResponse(
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val version: Long,
    val appliedRevision: Long,
    val effectiveFrom: YearMonth,
    val currency: String,
    val terms: CompensationTermsResponse,
)
