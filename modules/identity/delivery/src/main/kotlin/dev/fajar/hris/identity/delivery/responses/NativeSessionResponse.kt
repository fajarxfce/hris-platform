package dev.fajar.hris.identity.delivery.responses

import java.util.UUID

data class NativeSessionResponse(
    val id: UUID,
    val deviceName: String,
    val createdAt: String,
    val expiresAt: String,
)
