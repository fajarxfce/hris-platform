package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollPeriodMemberResponse(
    val employeeId: UUID,
    val inputVersion: Long?,
    val inputStatus: String?,
)
