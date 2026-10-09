package dev.fajar.hris.payroll.domain.entities

import java.time.YearMonth
import java.util.UUID

/**
 * A payroll read projection of immutable workforce evidence, without workforce implementation
 * types.
 */
data class PayrollWorkSource(
    val periodId: UUID,
    val earningsMonth: YearMonth,
    val version: Long,
    val closed: Boolean,
    val jobId: UUID?,
    val includesEmployee: Boolean,
)
