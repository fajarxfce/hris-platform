package dev.fajar.hris.payroll.data.models

import java.util.UUID

data class PayrollPeriodMemberRow(
    val employeeId: UUID,
    val inputVersion: Long?,
    val inputStatus: String?,
)
