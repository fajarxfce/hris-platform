package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollPeriodMemberResponse(
    val employeeId: UUID,
    val employeeNumber: String,
    val name: String,
    val inputVersion: Long?,
    val inputStatus: String?,
)
