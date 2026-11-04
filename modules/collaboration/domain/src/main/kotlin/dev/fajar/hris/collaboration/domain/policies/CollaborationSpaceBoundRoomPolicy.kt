package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

/** Owned metadata only; callers separately admit current sender and receiving peers. */
fun snapshotCollaborationSpaceBoundRoom(
    spaceId: UUID,
    accountId: UUID,
    serverName: String,
    roomId: String,
    value: CollaborationSpaceBoundRoom?,
    at: Instant,
): Result<CollaborationSpaceBoundRoom> {
    if (value == null)
        return Result.Failed(Failure(FailureKind.NOT_FOUND, "collaboration_room_not_found"))
    val intent = value.intent
    val binding = value.binding
    val pair = setOf(intent.firstAccountId, intent.secondAccountId)
    val request =
        CollaborationSpaceMatrixDirectRoom(
            intent.spaceId,
            intent.conversationId,
            intent.firstAccountId,
            intent.secondAccountId,
            intent.firstMatrixUserId,
            intent.secondMatrixUserId,
        )
    if (
        spaceId == UUID(0, 0) ||
            accountId == UUID(0, 0) ||
            intent.spaceId != spaceId ||
            binding.spaceId != spaceId ||
            binding.conversationId != intent.conversationId ||
            intent.firstAccountId.toString() >= intent.secondAccountId.toString() ||
            accountId !in pair ||
            intent.requestedBy !in pair ||
            intent.matrixCreatorAccountId !in pair ||
            binding.publishedBy !in pair ||
            binding.matrixCreatorAccountId != intent.matrixCreatorAccountId ||
            binding.leaseToken == UUID(0, 0) ||
            binding.requestRevision !in 0..999999 ||
            intent.serverName != serverName ||
            !validCollaborationSpaceRoomRequest(request, serverName) ||
            binding.roomId != roomId ||
            !validCollaborationSpaceRoomId(roomId) ||
            !boundRoomTime(intent.createdAt) ||
            !boundRoomTime(binding.createdAt) ||
            binding.createdAt < intent.createdAt ||
            binding.createdAt > at
    )
        return invalidBoundRoom()
    val original = intent.original
    when (intent.origin) {
        CollaborationSpaceRoomOrigin.SPACE ->
            if (original != null || intent.matrixCreatorAccountId != intent.requestedBy)
                return invalidBoundRoom()
        CollaborationSpaceRoomOrigin.LEGACY -> {
            if (
                original == null ||
                    original.companyId == UUID(0, 0) ||
                    original.conversationId == UUID(0, 0) ||
                    original.roomId != roomId ||
                    !boundRoomTime(original.boundAt) ||
                    original.boundAt > intent.createdAt ||
                    intent.firstMatrixUserId !=
                        "@${collaborationMatrixLocalpart(original.companyId, intent.firstAccountId)}:$serverName" ||
                    intent.secondMatrixUserId !=
                        "@${collaborationMatrixLocalpart(original.companyId, intent.secondAccountId)}:$serverName"
            )
                return invalidBoundRoom()
        }
    }
    return Result.Success(value)
}

private fun boundRoomTime(at: Instant) =
    at.nano % 1000 == 0 && at.epochSecond in -62135596800L..253402300799L

private fun invalidBoundRoom() =
    Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_room_scope_invalid"))
