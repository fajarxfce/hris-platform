package dev.fajar.hris.collaboration.delivery.responses

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceGatewayBootstrap
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceGatewayBootstrapIntent

/** The envelope belongs to the new operation; the original receives no invented acknowledgment. */
class NativeCollaborationSpaceBootstrapReplacementResponse(
    bootstrap: CollaborationSpaceGatewayBootstrap
) {
    val superseded =
        SupersededCollaborationSpaceBootstrapResponse(
            requireNotNull(bootstrap.replacement).superseded
        )
    val replacementOperationId = requireNotNull(bootstrap.replacement).replacementOperationId
    val gateway = NativeCollaborationSpaceBootstrapResponse(bootstrap)

    override fun toString() = "NativeCollaborationSpaceBootstrapReplacementResponse(<redacted>)"
}

/** The fence covers these effect coordinates, not an arbitrary request sharing only its UUID. */
class SupersededCollaborationSpaceBootstrapResponse(
    intent: CollaborationSpaceGatewayBootstrapIntent
) {
    val operationId = intent.operationId
    val spaceId = intent.spaceId
    val accountId = intent.accountId
    val nativeSessionId = intent.nativeSessionId
    val deviceId = intent.deviceId
    val expectedDeviceVersion = intent.expectedDeviceVersion
    val expectedGatewayVersion = intent.expectedGatewayVersion
    val platform = intent.client.platform
    val clientBuild = intent.client.build

    override fun toString() = "SupersededCollaborationSpaceBootstrapResponse(<redacted>)"
}
