package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollOvertimeEvidenceResponse(
    val requestId: UUID,
    val revision: Long,
    val minutes: Int,
)
