package dev.fajar.hris.collaboration.delivery.requests

import java.util.UUID

data class BootstrapCollaborationGatewayRequest(
    val deviceId: UUID,
    val expectedDeviceVersion: Long,
    val expectedGatewayVersion: Long?,
)
