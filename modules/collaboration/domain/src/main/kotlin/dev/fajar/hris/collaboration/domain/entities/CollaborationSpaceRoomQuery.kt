package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Both references resolve through the same current native room admission. */
sealed interface CollaborationSpaceRoomQuery {
    data class Conversation(val id: UUID) : CollaborationSpaceRoomQuery

    /** The configured provider owns the server name; callers supply only an opaque room ID. */
    data class ProviderRoom(val id: String) : CollaborationSpaceRoomQuery {
        override fun toString() = "ProviderRoom(<reference>)"
    }
}
