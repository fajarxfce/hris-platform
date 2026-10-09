package dev.fajar.hris.workforce.delivery.responses

import java.time.*
import java.util.UUID

data class OvertimeResponse(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val requesterAccountId: UUID?,
    val authorId: UUID,
    val createdAt: Instant,
    val workDate: LocalDate,
    val timezone: String,
    val schedule: ScheduledDayResponse,
    val requested: OvertimeIntervalResponse,
    val reason: String,
    val status: String,
    val actual: OvertimeIntervalResponse?,
    val submittedBy: UUID?,
    val submittedAt: Instant?,
    val approvalId: UUID?,
    val approvedMinutes: Int,
    val decidedAt: Instant?,
    val version: Long,
)
