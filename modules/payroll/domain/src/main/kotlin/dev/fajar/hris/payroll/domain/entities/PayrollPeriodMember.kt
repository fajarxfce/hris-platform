package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

data class PayrollPeriodMember(
    val employeeId: UUID,
    val employeeNumber: String,
    val name: String,
    val inputVersion: Long?,
    val inputStatus: PayrollInputStatus?,
)
