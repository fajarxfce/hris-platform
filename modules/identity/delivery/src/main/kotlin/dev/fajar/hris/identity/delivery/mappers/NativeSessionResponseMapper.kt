package dev.fajar.hris.identity.delivery.mappers

import dev.fajar.hris.identity.delivery.responses.*
import dev.fajar.hris.identity.domain.entities.*

fun NativeTokens.toResponse(): NativeTokensResponse =
    NativeTokensResponse(
        sessionId,
        accessToken,
        refreshToken,
        accessExpiresAt.toString(),
        sessionExpiresAt.toString(),
    )

fun NativeSession.toResponse(): NativeSessionResponse =
    NativeSessionResponse(id, deviceName, createdAt.toString(), expiresAt.toString())
