package dev.fajar.hris.payroll.delivery.requests

import java.time.*
import java.util.UUID

data class PayrollCalculationStartRequest(
    val id: UUID,
    val incomeDueDate: LocalDate,
    val expectedPeriodVersion: Long,
    val expectedWorkPeriodVersion: Long,
    val expectedPolicyVersion: Long,
    val reviewReference: String,
    val reason: String,
)
