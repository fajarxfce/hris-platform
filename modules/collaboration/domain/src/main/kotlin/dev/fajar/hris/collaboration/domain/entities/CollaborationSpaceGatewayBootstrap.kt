package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Identity and credentials captured together under the native/device transaction guards. */
class CollaborationSpaceGatewayBootstrap(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val tokens: CollaborationSpaceGatewayTokens,
    val replacement: CollaborationSpaceGatewayReplacementReceipt? = null,
) {
    override fun toString() = "CollaborationSpaceGatewayBootstrap(<redacted>)"
}
