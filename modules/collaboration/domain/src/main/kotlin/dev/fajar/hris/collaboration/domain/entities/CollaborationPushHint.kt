package dev.fajar.hris.collaboration.domain.entities

/** Provider identifiers are wake hints, never message content or permission to read a room. */
data class CollaborationPushEvent(val roomId: String, val eventId: String) {
    override fun toString() = "CollaborationPushEvent(<redacted>)"
}

class CollaborationPushHint(val event: CollaborationPushEvent?, keys: List<CollaborationPushKey>) {
    val keys: List<CollaborationPushKey> = keys.toList()

    override fun toString() = "CollaborationPushHint(<redacted>)"
}
