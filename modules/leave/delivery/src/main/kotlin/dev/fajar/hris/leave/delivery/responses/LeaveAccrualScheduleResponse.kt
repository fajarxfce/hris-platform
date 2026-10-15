package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class LeaveAccrualScheduleResponse(
    val id: UUID,
    val typeId: UUID,
    val typeCode: String,
    val typeName: String,
    val policyVersion: Long,
    val frequency: String,
    val firstPeriod: YearMonth,
    val periodCount: Int,
    val totalEmployees: Int,
    val timezone: String,
    val actorId: UUID,
    val createdAt: Instant,
    val reason: String,
)
