package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.PayrollRunTargetsRecord
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.jooq.JSONB

data class PayrollRunItemRow(
    val target: PayrollRunTargetsRecord,
    val jobId: UUID,
    val completedAt: OffsetDateTime,
    val failureKind: String?,
    val failureCode: String?,
    val fields: JSONB,
    val parameters: JSONB,
    val taxableGross: BigDecimal?,
    val withheld: BigDecimal?,
    val takeHome: BigDecimal?,
)
