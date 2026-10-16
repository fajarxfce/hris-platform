package dev.fajar.hris.workforce.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

@Schema(
    requiredProperties =
        ["workDate", "reviewRevision", "clockIn", "clockOut", "decisions", "reason"]
)
data class AttendanceAdjustmentRequest(
    @param:JsonProperty(required = true) val workDate: LocalDate,
    @param:JsonProperty(required = true)
    @field:Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        pattern = "^[0-9a-f]{64}$",
        minLength = 64,
        maxLength = 64,
    )
    val reviewRevision: String,
    @param:JsonProperty(required = true) val clockIn: Instant?,
    @param:JsonProperty(required = true) val clockOut: Instant?,
    @field:Schema(minimum = "0", maximum = "1439") val breakMinutes: Int = 0,
    @param:JsonProperty(required = true)
    @field:ArraySchema(maxItems = 32, uniqueItems = true)
    val decisions: List<AttendanceEvidenceDecisionRequest>,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 1000)
    val reason: String,
)
