package dev.fajar.hris.workforce.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import dev.fajar.hris.workforce.domain.entities.AttendanceReviewDecision
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class AttendanceEvidenceDecisionRequest(
    @param:JsonProperty(required = true) val eventId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        minimum = "0",
        maximum = "9223372036854775806",
    )
    val expectedVersion: Long,
    @param:JsonProperty(required = true) val decision: AttendanceReviewDecision,
)
