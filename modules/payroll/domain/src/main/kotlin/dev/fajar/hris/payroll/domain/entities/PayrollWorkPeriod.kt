package dev.fajar.hris.payroll.domain.entities

import java.time.YearMonth
import java.util.UUID

data class PayrollWorkPeriod(
    val id: UUID,
    val month: YearMonth,
    val version: Long,
    val closed: Boolean,
    val jobId: UUID?,
    val timezone: String?,
)
