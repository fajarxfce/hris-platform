package dev.fajar.hris.collaboration.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/** Stable intent receipt lookup; current native proof is supplied by authentication. */
data class ReadCollaborationSpaceRoomIntentReceiptRequest(
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val operationId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val deviceId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "999999")
    val expectedDeviceVersion: Long,
    @field:Schema(minimum = "0", maximum = "999999") val expectedRequestRevision: Long? = null,
    val expectedRequestAccountId: UUID? = null,
)
