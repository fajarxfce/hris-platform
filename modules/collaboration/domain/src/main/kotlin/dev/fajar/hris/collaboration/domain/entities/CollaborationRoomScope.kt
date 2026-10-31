package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Immutable HRIS room ownership, independent of a peer's current ability to receive new keys. */
data class CollaborationOwnedRoom(
    val companyId: UUID,
    val conversationId: UUID,
    val firstAccountId: UUID,
    val secondAccountId: UUID,
    val creatorAccountId: UUID,
    val serverName: String,
    val roomId: String,
)

data class CollaborationRoomScope(val rooms: List<CollaborationOwnedRoom>, val digest: String)

data class CollaborationSyncOwner(
    val companyId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val serverName: String,
) {
    override fun toString() = "CollaborationSyncOwner(<redacted>)"
}

/** Provider positions are private and meaningful only inside the matching encrypted cursor. */
data class CollaborationSyncPosition(
    val scopeDigest: String,
    val providerSince: String,
    val providerFilterId: String,
) {
    override fun toString() = "CollaborationSyncPosition(<redacted>)"
}
