package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.PayrollAmendmentsRecord
import java.time.OffsetDateTime
import java.util.UUID

data class PayrollAmendmentSummaryRow(
    val header: PayrollAmendmentsRecord,
    val revision: Long,
    val status: String,
    val action: String,
    val latestCalculationId: UUID?,
    val actorId: UUID,
    val recordedAt: OffsetDateTime,
    val reason: String,
)
