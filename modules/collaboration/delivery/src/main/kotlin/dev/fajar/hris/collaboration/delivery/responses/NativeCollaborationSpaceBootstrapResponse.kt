package dev.fajar.hris.collaboration.delivery.responses

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceGatewayBootstrap

/** The use case supplies original provider identity atomically with its opaque token pair. */
class NativeCollaborationSpaceBootstrapResponse(bootstrap: CollaborationSpaceGatewayBootstrap) {
    val spaceId = bootstrap.spaceId
    val accountId = bootstrap.accountId
    val deviceId = bootstrap.deviceId
    val nativeSessionId = bootstrap.nativeSessionId
    val matrixUserId = bootstrap.matrixUserId
    val matrixDeviceId = bootstrap.matrixDeviceId
    val sessionId = bootstrap.tokens.sessionId
    val version = bootstrap.tokens.version
    val accessToken = bootstrap.tokens.accessToken
    val refreshToken = bootstrap.tokens.refreshToken
    val accessExpiresAt = bootstrap.tokens.accessExpiresAt
    val expiresAt = bootstrap.tokens.expiresAt

    override fun toString() = "NativeCollaborationSpaceBootstrapResponse(<redacted>)"
}
