package dev.fajar.hris.collaboration.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class ReadCollaborationSpaceLegacyRoomRequest(
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val companyId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 2, maxLength = 255)
    val matrixRoomId: String,
)
