package dev.fajar.hris.collaboration.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class VerifyCollaborationLegacyPublicationRequest(
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val deviceId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "999998")
    val expectedDeviceVersion: Long,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[a-f0-9]{64}$")
    val fingerprint: String,
)
