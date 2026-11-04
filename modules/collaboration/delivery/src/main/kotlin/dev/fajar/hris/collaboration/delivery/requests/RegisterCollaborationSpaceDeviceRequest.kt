package dev.fajar.hris.collaboration.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import dev.fajar.hris.collaboration.domain.entities.*
import io.swagger.v3.oas.annotations.media.Schema

data class RegisterCollaborationSpaceDeviceRequest(
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val platform: CollaborationPlatform,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val label: String,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val origin: CollaborationSpaceDeviceOrigin,
    val legacy: CollaborationLegacyDevicePosition? = null,
    val legacyKeyFingerprint: String? = null,
)
