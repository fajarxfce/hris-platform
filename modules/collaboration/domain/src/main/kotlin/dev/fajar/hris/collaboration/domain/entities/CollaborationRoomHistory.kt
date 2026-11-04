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

fun validCollaborationRoomHistoryPage(value: CollaborationRoomPageRequest) =
    value.direction in setOf("b", "f") &&
        value.limit in 1..100 &&
        validCollaborationRoomHistoryFilter(value.filter) &&
        listOfNotNull(value.from, value.to).all {
            it.length in 1..4096 && it.all { char -> char.code in 33..126 }
        }

fun validCollaborationRoomHistoryEventId(value: String) =
    value.length in 2..255 && value.startsWith('$') && value.all { it.code in 33..126 }

data class CollaborationRoomHistory(val payload: Map<String, Any>) {
    override fun toString() = "CollaborationRoomHistory(redacted)"
}
