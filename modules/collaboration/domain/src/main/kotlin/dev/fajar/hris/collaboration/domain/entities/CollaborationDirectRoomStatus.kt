package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

enum class CollaborationDirectRoomState {
    NOT_REQUESTED,
    QUEUED,
    RUNNING,
    READY,
    FAILED,
    CANCELLED,
}

data class CollaborationDirectRoomStatus(
    val conversationId: UUID,
    val version: Long?,
    val state: CollaborationDirectRoomState,
    val matrixRoomId: String?,
    val creatorAccountId: UUID?,
    val retryable: Boolean,
    val retryAt: Instant?,
)
