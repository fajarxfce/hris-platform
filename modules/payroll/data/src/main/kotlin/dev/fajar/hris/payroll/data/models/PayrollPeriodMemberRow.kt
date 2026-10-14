package dev.fajar.hris.payroll.data.models

import java.util.UUID

data class PayrollPeriodMemberRow(
    val employeeId: UUID,
    val employeeNumber: String,
    val name: String,
    val inputVersion: Long?,
    val inputStatus: String?,
)
