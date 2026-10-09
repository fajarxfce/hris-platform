package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID

data class PayrollRunItem(
    val target: PayrollRunTarget,
    val jobId: UUID,
    val completedAt: Instant,
    val failure: Failure?,
    val taxableGross: BigDecimal?,
    val withheld: BigDecimal?,
    val takeHome: BigDecimal?,
)
