package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollRunItemResponse(
    val target: PayrollRunTargetResponse,
    val jobId: UUID,
    val completedAt: Instant,
    val failure: PayrollRunFailureResponse?,
    val taxableGross: String?,
    val withheld: String?,
    val takeHome: String?,
)
