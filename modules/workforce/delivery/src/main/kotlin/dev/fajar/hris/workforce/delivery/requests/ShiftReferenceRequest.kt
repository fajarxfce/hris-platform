package dev.fajar.hris.workforce.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class ShiftReferenceRequest(
    @param:JsonProperty(required = true) val id: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    val version: Long,
)
