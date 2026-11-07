package dev.fajar.hris.collaboration.domain.entities

import dev.fajar.hris.core.domain.MutationReceipt
import java.time.Instant
import java.util.UUID

/** Immutable non-execution of one stable original; never room or message authority. */
data class CollaborationSpaceRoomRequestFence(
    val accountId: UUID,
    val original: CollaborationSpaceRoomRequestIntent,
    val id: UUID,
    val createdAt: Instant,
)

sealed interface CollaborationSpaceRoomRequestResolution {
    val accountId: UUID
    val original: CollaborationSpaceRoomRequestIntent

    data class Recorded(
        override val accountId: UUID,
        override val original: CollaborationSpaceRoomRequestIntent,
        val receipt: MutationReceipt,
    ) : CollaborationSpaceRoomRequestResolution

    data class Fenced(val evidence: CollaborationSpaceRoomRequestFence) :
        CollaborationSpaceRoomRequestResolution {
        override val accountId
            get() = evidence.accountId

        override val original
            get() = evidence.original
    }
}
