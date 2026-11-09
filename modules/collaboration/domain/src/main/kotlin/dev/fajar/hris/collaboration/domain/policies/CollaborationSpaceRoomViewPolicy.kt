package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

/** Exact publication facts. This policy performs no acquisition and grants no sending authority. */
fun collaborationSpaceRoomView(
    spaceId: UUID,
    conversationId: UUID,
    conversation: CollaborationSpaceDirectConversation,
    intent: CollaborationSpaceRoomIntent?,
    status: CollaborationSpaceRoomStatus?,
    binding: CollaborationSpaceRoomBinding?,
    serverName: String,
    at: Instant,
): Result<CollaborationSpaceRoomView> {
    if ((intent == null) != (status == null)) return changed()
    if (
        intent != null &&
            (intent.spaceId != spaceId ||
                intent.conversationId != conversationId ||
                intent.firstAccountId != conversation.firstAccountId ||
                intent.secondAccountId != conversation.secondAccountId ||
                intent.serverName != serverName ||
                intent.createdAt > at)
    )
        return changed()
    if (
        status != null &&
            (status.spaceId != spaceId ||
                status.conversationId != conversationId ||
                status.accountId !in
                    setOf(conversation.firstAccountId, conversation.secondAccountId) ||
                status.requestedAt > at ||
                status.completedAt?.let { it > at } == true)
    )
        return changed()
    if ((binding != null) != (status?.state == CollaborationSpaceProvisioningState.SUCCEEDED))
        return changed()
    if (
        binding != null &&
            (intent == null ||
                status == null ||
                binding.spaceId != spaceId ||
                binding.conversationId != conversationId ||
                binding.publishedBy != status.accountId ||
                binding.matrixCreatorAccountId !in
                    setOf(intent.firstAccountId, intent.secondAccountId) ||
                (intent.origin == CollaborationSpaceRoomOrigin.LEGACY &&
                    binding.matrixCreatorAccountId != intent.matrixCreatorAccountId) ||
                binding.requestRevision != status.revision ||
                binding.createdAt > at ||
                binding.createdAt < intent.createdAt)
    )
        return changed()
    val room =
        binding?.let {
            val owner = requireNotNull(intent)
            val original = owner.original
            if (!validCollaborationSpaceRoomId(it.roomId)) return changed()
            when (owner.origin) {
                CollaborationSpaceRoomOrigin.SPACE -> if (original != null) return changed()
                CollaborationSpaceRoomOrigin.LEGACY -> {
                    if (
                        original == null ||
                            original.companyId == UUID(0, 0) ||
                            original.conversationId == UUID(0, 0) ||
                            original.roomId != it.roomId ||
                            original.boundAt > owner.createdAt ||
                            original.boundAt.nano % 1000 != 0 ||
                            original.boundAt.epochSecond !in -62135596800L..253402300799L ||
                            owner.firstMatrixUserId !=
                                "@${collaborationMatrixLocalpart(original.companyId, owner.firstAccountId)}:${owner.serverName}" ||
                            owner.secondMatrixUserId !=
                                "@${collaborationMatrixLocalpart(original.companyId, owner.secondAccountId)}:${owner.serverName}"
                    )
                        return changed()
                }
            }
            CollaborationSpacePublishedRoom(
                it.roomId,
                owner.serverName,
                owner.origin,
                it.createdAt,
                original,
            )
        }
    return Result.Success(CollaborationSpaceRoomView(spaceId, conversationId, status, room))
}

private fun changed() =
    Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_provisioning_changed"))
