package dev.fajar.hris.collaboration.domain.entities

data class CollaborationRoomPageRequest(
    val from: String?,
    val to: String?,
    val direction: String,
    val limit: Int,
) {
    override fun toString() = "CollaborationRoomPageRequest(redacted)"
}

data class CollaborationRoomHistory(val payload: Map<String, Any>) {
    override fun toString() = "CollaborationRoomHistory(redacted)"
}
