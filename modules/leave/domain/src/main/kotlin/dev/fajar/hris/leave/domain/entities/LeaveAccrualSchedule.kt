package dev.fajar.hris.leave.domain.entities

import java.time.*
import java.util.UUID

data class LeaveAccrualSchedule(
    val id: UUID,
    val typeId: UUID,
    val typeCode: String,
    val typeName: String,
    val policyVersion: Long,
    val frequency: LeaveAccrualFrequency,
    val firstPeriod: YearMonth,
    val periodCount: Int,
    val totalEmployees: Int,
    val timezone: String,
    val actorId: UUID,
    val createdAt: Instant,
    val reason: String,
)
