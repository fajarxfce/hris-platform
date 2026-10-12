package dev.fajar.hris.workforce.delivery.responses

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AttendanceExceptionResponse(
    val employeeId: UUID,
    @field:Schema(maxLength = 32) val employeeNumber: String,
    @field:Schema(maxLength = 200) val employeeName: String,
    val workDate: LocalDate,
    @field:Schema(minimum = "0", maximum = "32") val pendingCount: Int,
    val incomplete: Boolean,
    @field:Schema(minimum = "0") val correctionVersion: Long?,
    val lastReceivedAt: Instant,
)
