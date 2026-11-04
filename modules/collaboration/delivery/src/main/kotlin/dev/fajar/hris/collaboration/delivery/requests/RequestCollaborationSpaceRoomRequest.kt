package dev.fajar.hris.collaboration.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class RequestCollaborationSpaceRoomRequest(
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val deviceId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "999999")
    val expectedDeviceVersion: Long,
    @field:Schema(
        minimum = "0",
        maximum = "999999",
        description =
            "Observed request revision; supply together with expectedRequestAccountId for terminal recovery.",
    )
    val expectedRequestRevision: Long? = null,
    @field:Schema(
        description =
            "Owner of the observed request revision; may be either conversation participant."
    )
    val expectedRequestAccountId: UUID? = null,
)
