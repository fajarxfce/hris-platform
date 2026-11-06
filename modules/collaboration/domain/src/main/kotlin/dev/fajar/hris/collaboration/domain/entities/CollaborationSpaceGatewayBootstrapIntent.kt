package dev.fajar.hris.collaboration.domain.entities

import dev.fajar.hris.administration.domain.entities.ClientVersion
import java.util.UUID

/** Original effect coordinates, including historical actual build; never live admission. */
data class CollaborationSpaceGatewayBootstrapIntent(
    val operationId: UUID,
    val spaceId: UUID,
    val accountId: UUID,
    val nativeSessionId: UUID,
    val deviceId: UUID,
    val expectedDeviceVersion: Long,
    val expectedGatewayVersion: Long?,
    val client: ClientVersion,
) {
    override fun toString() = "CollaborationSpaceGatewayBootstrapIntent(<redacted>)"
}

/** A committed newer head fences this exact original; it does not say whether it once applied. */
data class CollaborationSpaceGatewayReplacementReceipt(
    val superseded: CollaborationSpaceGatewayBootstrapIntent,
    val replacementOperationId: UUID,
) {
    override fun toString() = "CollaborationSpaceGatewayReplacementReceipt(<redacted>)"
}
