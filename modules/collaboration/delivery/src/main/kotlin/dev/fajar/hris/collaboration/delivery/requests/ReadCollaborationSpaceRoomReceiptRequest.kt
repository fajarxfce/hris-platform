package dev.fajar.hris.collaboration.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import dev.fajar.hris.administration.domain.entities.ClientPlatform
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * Original public request coordinates only; current authentication comes from the native bearer.
 */
data class ReadCollaborationSpaceRoomReceiptRequest(
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val operationId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val nativeSessionId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "999999")
    val nativeSessionVersion: Long,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val deviceId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "999999")
    val expectedDeviceVersion: Long,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = ["ANDROID", "IOS"])
    val platform: ClientPlatform,
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    val clientBuild: Int,
    @field:Schema(
        minimum = "0",
        maximum = "999999",
        description = "Original observed revision; supply with its original request account.",
    )
    val expectedRequestRevision: Long? = null,
    val expectedRequestAccountId: UUID? = null,
)
