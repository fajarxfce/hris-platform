package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

data class PayrollPeriodMember(
    val employeeId: UUID,
    val inputVersion: Long?,
    val inputStatus: PayrollInputStatus?,
)
