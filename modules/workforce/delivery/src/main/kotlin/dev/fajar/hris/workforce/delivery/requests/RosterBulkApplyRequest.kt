package dev.fajar.hris.workforce.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

data class RosterBulkApplyRequest(
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val selection: RosterBulkReviewRequest,
    @param:JsonProperty(required = true)
    @field:Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        pattern = "^[0-9a-f]{64}$",
        minLength = 64,
        maxLength = 64,
    )
    val reviewRevision: String,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 1000)
    val reason: String,
)
