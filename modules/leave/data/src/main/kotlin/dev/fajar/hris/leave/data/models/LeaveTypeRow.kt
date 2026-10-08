package dev.fajar.hris.leave.data.models

import java.time.LocalDate
import java.util.UUID
import org.jooq.JSONB

data class LeaveTypeRow(
    val id: UUID,
    val code: String,
    val version: Long,
    val revision: Long,
    val effectiveFrom: LocalDate,
    val details: JSONB,
    val active: Boolean,
)
