package dev.fajar.hris.collaboration.delivery.responses

import dev.fajar.hris.collaboration.domain.entities.CollaborationGatewayTokens

/** Tokens belong only in the private no-store response, never in generated data-class strings. */
class NativeCollaborationBootstrapResponse(
    tokens: CollaborationGatewayTokens,
    val matrixUserId: String,
    val matrixDeviceId: String,
) {
    val sessionId = tokens.sessionId
    val version = tokens.version
    val accessToken = tokens.accessToken
    val refreshToken = tokens.refreshToken
    val accessExpiresAt = tokens.accessExpiresAt
    val expiresAt = tokens.expiresAt

    override fun toString() = "NativeCollaborationBootstrapResponse(<redacted>)"
}
