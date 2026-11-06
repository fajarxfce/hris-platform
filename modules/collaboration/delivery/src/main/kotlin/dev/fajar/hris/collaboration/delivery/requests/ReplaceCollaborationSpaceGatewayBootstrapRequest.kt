package dev.fajar.hris.collaboration.delivery.requests

import com.fasterxml.jackson.annotation.JsonProperty
import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.CollaborationPlatform
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceGatewayBootstrapIntent
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class SupersededCollaborationSpaceBootstrapRequest(
    @param:JsonProperty(required = true) val operationId: UUID,
    @param:JsonProperty(required = true) val spaceId: UUID,
    @param:JsonProperty(required = true) val accountId: UUID,
    @param:JsonProperty(required = true) val nativeSessionId: UUID,
    @param:JsonProperty(required = true) val deviceId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(minimum = "0", maximum = "999999")
    val expectedDeviceVersion: Long,
    @field:Schema(minimum = "0", maximum = "999998") val expectedGatewayVersion: Long? = null,
    @param:JsonProperty(required = true) val platform: CollaborationPlatform,
    @param:JsonProperty(required = true)
    @field:Schema(minimum = "1", maximum = "999999999")
    val clientBuild: Int,
) {
    override fun toString() = "SupersededCollaborationSpaceBootstrapRequest(<redacted>)"

    fun toIntent() =
        CollaborationSpaceGatewayBootstrapIntent(
            operationId,
            spaceId,
            accountId,
            nativeSessionId,
            deviceId,
            expectedDeviceVersion,
            expectedGatewayVersion,
            ClientVersion(ClientPlatform.valueOf(platform.name), clientBuild),
        )
}

data class ReplaceCollaborationSpaceGatewayBootstrapRequest(
    @param:JsonProperty(required = true) val deviceId: UUID,
    @param:JsonProperty(required = true)
    @field:Schema(minimum = "0", maximum = "999999")
    val expectedDeviceVersion: Long,
    @field:Schema(minimum = "0", maximum = "999998") val expectedGatewayVersion: Long? = null,
    @param:JsonProperty(required = true)
    val supersedes: SupersededCollaborationSpaceBootstrapRequest,
) {
    override fun toString() = "ReplaceCollaborationSpaceGatewayBootstrapRequest(<redacted>)"
}
