package dev.fajar.hris.collaboration.domain.entities

/** Immutable DIRECT room metadata, independent of current peer receiving eligibility. */
data class CollaborationSpaceRoomScope(
    val rooms: List<CollaborationSpaceBoundRoom>,
    val digest: String,
)
