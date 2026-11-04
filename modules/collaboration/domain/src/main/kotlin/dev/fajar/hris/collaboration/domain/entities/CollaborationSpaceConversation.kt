package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Public selection metadata only; it does not establish a receiving device or room grant. */
data class CollaborationSpacePublicIdentity(
    val serverName: String,
    val userId: String,
    val selectedAt: Instant,
)

data class CollaborationSpaceMember(
    val spaceId: UUID,
    val accountId: UUID,
    val displayName: String,
    val identity: CollaborationSpacePublicIdentity?,
)

/** Durable group pair, independently of whether either participant has initialized Matrix. */
data class CollaborationSpaceDirectConversation(
    val id: UUID,
    val spaceId: UUID,
    val firstAccountId: UUID,
    val secondAccountId: UUID,
    val createdBy: UUID,
    val createdAt: Instant,
    val version: Long = 0,
)

data class CollaborationLegacyConversationPosition(val companyId: UUID, val conversationId: UUID)

data class CollaborationLegacyRoomMetadata(
    val serverName: String,
    val roomId: String,
    val boundAt: Instant,
    val createdBy: UUID,
)

/** The original company conversation remains explicit; this is not an adopted group room. */
data class CollaborationSpaceLegacyConversation(
    val spaceId: UUID,
    val conversation: CollaborationDirectConversation,
    val room: CollaborationLegacyRoomMetadata?,
)

data class CollaborationSpaceLegacyConversationPage(
    val items: List<CollaborationSpaceLegacyConversation>,
    val next: CollaborationLegacyConversationPosition?,
)
