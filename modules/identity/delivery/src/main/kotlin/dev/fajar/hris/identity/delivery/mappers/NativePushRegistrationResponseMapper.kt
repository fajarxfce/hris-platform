package dev.fajar.hris.identity.delivery.mappers

import dev.fajar.hris.identity.delivery.responses.NativePushRegistrationResponse
import dev.fajar.hris.identity.domain.entities.NativePushRegistration

fun NativePushRegistration.toResponse() =
    NativePushRegistrationResponse(
        sessionId,
        platform.name,
        enabled,
        version,
        registeredAt,
        updatedAt,
        expiresAt,
    )
