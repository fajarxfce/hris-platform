package dev.fajar.hris.workforce.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import dev.fajar.hris.workforce.domain.entities.RosterBulkMode
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

@Schema(requiredProperties = ["employeeIds", "dates", "mode"])
data class RosterBulkReviewRequest(
    @param:JsonProperty(required = true)
    @field:ArraySchema(minItems = 1, maxItems = 50, uniqueItems = true)
    val employeeIds: List<UUID>,
    @param:JsonProperty(required = true)
    @field:ArraySchema(minItems = 1, maxItems = 31, uniqueItems = true)
    val dates: List<LocalDate>,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val mode: RosterBulkMode,
    val shift: ShiftReferenceRequest? = null,
)
