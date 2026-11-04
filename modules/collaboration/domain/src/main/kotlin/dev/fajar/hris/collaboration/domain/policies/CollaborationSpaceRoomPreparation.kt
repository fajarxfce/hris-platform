package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

/** Immutable public coordinates. Current native, group and device admission is separate. */
data class CollaborationSpaceRoomParticipants(
    val conversation: CollaborationSpaceDirectConversation,
    val first: CollaborationSpaceMember,
    val second: CollaborationSpaceMember,
    val serverName: String,
    val originalCompanyId: UUID?,
)

fun prepareCollaborationSpaceRoomParticipants(
    conversation: CollaborationSpaceDirectConversation,
    first: CollaborationSpaceMember?,
    second: CollaborationSpaceMember?,
    serverName: String,
    at: Instant,
): Result<CollaborationSpaceRoomParticipants> {
    if (
        first == null ||
            second == null ||
            first.spaceId != conversation.spaceId ||
            second.spaceId != conversation.spaceId ||
            first.accountId != conversation.firstAccountId ||
            second.accountId != conversation.secondAccountId
    )
        return roomPeerUnavailable()
    val firstIdentity = first.identity ?: return roomPeerUnavailable()
    val secondIdentity = second.identity ?: return roomPeerUnavailable()
    if (
        conversation.firstAccountId.toString() >= conversation.secondAccountId.toString() ||
            conversation.createdBy !in
                setOf(conversation.firstAccountId, conversation.secondAccountId) ||
            conversation.version != 0L ||
            firstIdentity.serverName != serverName ||
            secondIdentity.serverName != serverName ||
            listOf(conversation.createdAt, firstIdentity.selectedAt, secondIdentity.selectedAt)
                .any { !roomMetadataTime(it) || it > at } ||
            !roomMetadataTime(at)
    )
        return roomPreparationChanged()
    val request =
        CollaborationSpaceMatrixDirectRoom(
            conversation.spaceId,
            conversation.id,
            conversation.firstAccountId,
            conversation.secondAccountId,
            firstIdentity.userId,
            secondIdentity.userId,
        )
    if (!validCollaborationSpaceRoomRequest(request, serverName)) return roomPreparationChanged()
    val firstOriginal = originalMatrixCompany(firstIdentity.userId)
    val secondOriginal = originalMatrixCompany(secondIdentity.userId)
    val company = firstOriginal?.takeIf { it == secondOriginal }
    return Result.Success(
        CollaborationSpaceRoomParticipants(conversation, first, second, serverName, company)
    )
}

/**
 * Caller has performed the exact bounded original lookup under the optional original pair guard.
 */
fun prepareCollaborationSpaceRoomIntent(
    participants: CollaborationSpaceRoomParticipants,
    requester: UUID,
    original: CollaborationSpaceLegacyConversation?,
    at: Instant,
): Result<CollaborationSpaceRoomIntent> {
    val c = participants.conversation
    val checked =
        prepareCollaborationSpaceRoomParticipants(
            c,
            participants.first,
            participants.second,
            participants.serverName,
            at,
        )
    if (checked is Result.Failed) return checked
    if (
        (checked as Result.Success).value != participants ||
            requester !in setOf(c.firstAccountId, c.secondAccountId)
    )
        return roomPreparationChanged()
    val first = requireNotNull(participants.first.identity)
    val second = requireNotNull(participants.second.identity)
    var retained: CollaborationSpaceOriginalRoom? = null
    var creator = requester
    if (original != null) {
        if (participants.originalCompanyId != original.conversation.companyId)
            return roomPreparationChanged()
        if (original.room == null)
            return Result.Failed(
                Failure(FailureKind.CONFLICT, "collaboration_original_room_recovery_required")
            )
        val prepared =
            prepareCollaborationSpaceLegacyRoom(
                c,
                participants.first,
                participants.second,
                original,
                participants.serverName,
                at,
            )
        if (prepared is Result.Failed) return prepared
        val owned = (prepared as Result.Success).value
        val source = requireNotNull(original.room)
        creator = source.createdBy
        retained =
            CollaborationSpaceOriginalRoom(
                original.conversation.companyId,
                original.conversation.id,
                source.roomId,
                source.boundAt,
            )
        // The existing pure verifier must retain exactly these coordinates.
        if (owned.roomId != source.roomId) return roomPreparationChanged()
    }
    return Result.Success(
        CollaborationSpaceRoomIntent(
            c.spaceId,
            c.id,
            c.firstAccountId,
            c.secondAccountId,
            requester,
            creator,
            participants.serverName,
            first.userId,
            second.userId,
            if (retained == null) CollaborationSpaceRoomOrigin.SPACE
            else CollaborationSpaceRoomOrigin.LEGACY,
            retained,
            at,
        )
    )
}

private fun originalMatrixCompany(user: String): UUID? {
    if (!user.startsWith("@hris_")) return null
    // Called only after exact canonical user validation above.
    val raw = user.substring(6, 38)
    return UUID(raw.substring(0, 16).toULong(16).toLong(), raw.substring(16).toULong(16).toLong())
}

private fun roomMetadataTime(at: Instant) =
    at.nano % 1000 == 0 && at.epochSecond in -62135596800L..253402300799L

private fun roomPreparationChanged() =
    Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_provisioning_changed"))

private fun roomPeerUnavailable() =
    Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_peer_unavailable"))
