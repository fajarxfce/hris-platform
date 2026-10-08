package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class NativeRefresh(
    val session: NativeSession,
    val consumed: Boolean,
    val operationId: UUID? = null,
    val successorVersion: Long? = null,
    val replayUntil: Instant? = null,
)
