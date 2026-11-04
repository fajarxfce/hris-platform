package dev.fajar.hris.collaboration.domain.entities

/** Original and published ownership remain explicit even when they describe the same room. */
sealed interface CollaborationSpaceReadRoom {
    val roomId: String

    data class Published(
        val value: CollaborationSpaceBoundRoom,
        val original: CollaborationSpaceLegacyConversation? = null,
    ) : CollaborationSpaceReadRoom {
        override val roomId: String
            get() = value.binding.roomId
    }

    data class Original(val value: CollaborationSpaceLegacyConversation) :
        CollaborationSpaceReadRoom {
        override val roomId: String
            get() = requireNotNull(value.room).roomId
    }
}

/** Bounded immutable DIRECT read inventory; it does not grant current receiving authority. */
data class CollaborationSpaceReadScope(
    val rooms: List<CollaborationSpaceReadRoom>,
    val digest: String,
)
