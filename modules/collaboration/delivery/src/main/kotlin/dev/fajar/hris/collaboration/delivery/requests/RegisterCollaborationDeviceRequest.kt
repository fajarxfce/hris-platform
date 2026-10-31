package dev.fajar.hris.collaboration.delivery.requests

import dev.fajar.hris.collaboration.domain.entities.CollaborationPlatform

data class RegisterCollaborationDeviceRequest(
    val platform: CollaborationPlatform,
    val label: String,
)
