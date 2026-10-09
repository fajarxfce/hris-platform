package dev.fajar.hris.workforce.delivery.requests

import java.time.LocalDate
import java.util.UUID

data class OvertimePlanRequest(
    val id: UUID,
    val employeeId: UUID,
    val expectedEmploymentVersion: Long,
    val workDate: LocalDate,
    val requested: OvertimeIntervalRequest,
    val reason: String,
)
