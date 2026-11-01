package dev.fajar.hris.collaboration.domain.entities

data class CollaborationRoomPageRequest(
    val from: String?,
    val to: String?,
    val direction: String,
    val limit: Int,
    val filter: CollaborationRoomHistoryFilter = CollaborationRoomHistoryFilter(),
) {
    override fun toString() = "CollaborationRoomPageRequest(redacted)"
}

/** Membership-state loading only; event/sender filters would need their own scoped cursors. */
data class CollaborationRoomHistoryFilter(
    val lazyLoadMembers: Boolean = false,
    val includeRedundantMembers: Boolean = false,
)

fun validCollaborationRoomHistoryFilter(value: CollaborationRoomHistoryFilter) =
    !value.includeRedundantMembers || value.lazyLoadMembers

data class CollaborationRoomHistory(val payload: Map<String, Any>) {
    override fun toString() = "CollaborationRoomHistory(redacted)"
}
