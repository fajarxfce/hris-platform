package dev.fajar.hris.collaboration.domain.entities

/** Owned conversation with the counterpart's current label; this is not a new-send grant. */
data class CollaborationDirectSummary(
    val conversation: CollaborationDirectConversation,
    val peer: CollaborationParticipantReference,
)
