package dev.fajar.hris.collaboration.delivery.requests

import java.util.UUID

data class RetryCollaborationProvisioningRequest(
    val deviceId: UUID,
    val expectedDeviceVersion: Long,
    val expectedProvisioningVersion: Long,
)
