package dev.fajar.hris.collaboration.domain.entities

/**
 * Immutable owned room metadata; no provider or current recipient authority follows from lookup.
 */
data class CollaborationSpaceBoundRoom(
    val intent: CollaborationSpaceRoomIntent,
    val binding: CollaborationSpaceRoomBinding,
) {
    override fun toString() = "CollaborationSpaceBoundRoom(<metadata>)"
}
