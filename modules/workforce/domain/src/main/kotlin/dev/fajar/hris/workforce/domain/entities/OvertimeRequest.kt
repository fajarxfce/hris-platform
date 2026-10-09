package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class OvertimeRequest(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val requesterAccountId: UUID?,
    val authorId: UUID,
    val createdAt: Instant,
    val workDate: LocalDate,
    val timezone: String,
    val schedule: ScheduledDay,
    val requested: OvertimeInterval,
    val reason: String,
    val status: OvertimeStatus = OvertimeStatus.PLANNED,
    val actual: OvertimeInterval? = null,
    val submittedBy: UUID? = null,
    val submittedAt: Instant? = null,
    val approvalId: UUID? = null,
    val approvedMinutes: Int = 0,
    val decidedAt: Instant? = null,
    val version: Long = 0,
)
