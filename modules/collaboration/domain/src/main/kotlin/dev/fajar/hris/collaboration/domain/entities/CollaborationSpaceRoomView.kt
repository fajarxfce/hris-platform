package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Safe native status only; no provisioning authentication proof or worker lease token. */
data class CollaborationSpaceRoomView(
    val spaceId: UUID,
    val conversationId: UUID,
    val provisioning: CollaborationSpaceRoomStatus?,
    val room: CollaborationSpacePublishedRoom?,
)

data class CollaborationSpacePublishedRoom(
    val roomId: String,
    val serverName: String,
    val origin: CollaborationSpaceRoomOrigin,
    val publishedAt: Instant,
)
