package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

/** Original ownership only; fresh and retained devices still need current native admission. */
fun snapshotCollaborationSpaceOriginalReadRoom(
    owner: CollaborationSpaceHistoryOwner,
    value: CollaborationSpaceLegacyConversation,
    at: Instant,
): Result<CollaborationSpaceLegacyConversation> {
    val company = collaborationSpaceHistoryOriginalCompany(owner) ?: return invalidReadScope()
    val conversation = value.conversation
    val room = value.room ?: return invalidReadScope()
    val pair = setOf(conversation.firstAccountId, conversation.secondAccountId)
    if (
        value.spaceId != owner.spaceId ||
            conversation.companyId != company ||
            conversation.id == UUID(0, 0) ||
            conversation.firstAccountId == UUID(0, 0) ||
            conversation.secondAccountId == UUID(0, 0) ||
            conversation.firstAccountId.toString() >= conversation.secondAccountId.toString() ||
            owner.accountId !in pair ||
            conversation.createdBy !in pair ||
            room.createdBy !in pair ||
            conversation.version != 0L ||
            room.serverName != owner.serverName ||
            !validCollaborationSpaceRoomId(room.roomId) ||
            !readScopeTime(conversation.createdAt) ||
            !readScopeTime(room.boundAt) ||
            room.boundAt < conversation.createdAt ||
            room.boundAt > at ||
            at.epochSecond !in -62135596800L..253402300799L
    )
        return invalidReadScope()
    return Result.Success(value)
}

/** Complete immutable DIRECT union; peer runtime state never removes retained owned history. */
fun snapshotCollaborationSpaceReadScope(
    owner: CollaborationSpaceHistoryOwner,
    published: List<CollaborationSpaceBoundRoom>,
    original: List<CollaborationSpaceLegacyConversation>,
    at: Instant,
): Result<CollaborationSpaceReadScope> {
    if (
        published.size > COLLABORATION_SYNC_ROOM_LIMIT ||
            original.size > COLLABORATION_SYNC_ROOM_LIMIT
    )
        return readScopeCapacity()
    if (
        !validCollaborationSpaceHistoryIdentity(owner) ||
            at.epochSecond !in -62135596800L..253402300799L
    )
        return invalidReadScope()
    val groupResult =
        snapshotCollaborationSpaceRoomScope(
            owner.spaceId,
            owner.accountId,
            owner.serverName,
            published,
            at,
        )
    if (groupResult is Result.Failed) return groupResult
    val group = (groupResult as Result.Success).value
    val originals = original.toList().sortedBy { it.conversation.id.toString() }
    val byConversation = linkedMapOf<UUID, CollaborationSpaceLegacyConversation>()
    val byRoom = linkedMapOf<String, CollaborationSpaceLegacyConversation>()
    for (value in originals) {
        val checked = snapshotCollaborationSpaceOriginalReadRoom(owner, value, at)
        if (checked is Result.Failed) return checked
        if (
            byConversation.put(value.conversation.id, value) != null ||
                byRoom.put(requireNotNull(value.room).roomId, value) != null
        )
            return invalidReadScope()
    }
    val rooms = linkedMapOf<String, CollaborationSpaceReadRoom>()
    for (bound in group.rooms) {
        if (!validCollaborationSpaceHistoryOwner(owner, bound)) return invalidReadScope()
        val intent = bound.intent
        val originalValue = byRoom[bound.binding.roomId]
        when (intent.origin) {
            CollaborationSpaceRoomOrigin.SPACE ->
                if (originalValue != null) return invalidReadScope()
            CollaborationSpaceRoomOrigin.LEGACY -> {
                val reference = intent.original ?: return invalidReadScope()
                val old = originalValue ?: return invalidReadScope()
                val oldRoom = old.room ?: return invalidReadScope()
                if (
                    reference.companyId != old.conversation.companyId ||
                        reference.conversationId != old.conversation.id ||
                        reference.boundAt != oldRoom.boundAt ||
                        intent.firstAccountId != old.conversation.firstAccountId ||
                        intent.secondAccountId != old.conversation.secondAccountId ||
                        intent.matrixCreatorAccountId != oldRoom.createdBy
                )
                    return invalidReadScope()
            }
        }
        rooms[bound.binding.roomId] = CollaborationSpaceReadRoom.Published(bound, originalValue)
    }
    for ((id, value) in byRoom) rooms.putIfAbsent(id, CollaborationSpaceReadRoom.Original(value))
    if (rooms.size > COLLABORATION_SYNC_ROOM_LIMIT) return readScopeCapacity()
    val digest = MessageDigest.getInstance("SHA-256")
    fun add(value: Any?) {
        digest.update((value?.toString() ?: "").toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
    }
    add("organization-direct-read-v1")
    add(owner.spaceId)
    add(owner.accountId)
    add(owner.deviceId)
    add(owner.serverName)
    add(owner.matrixUserId)
    add(owner.matrixDeviceId)
    add(owner.legacy?.companyId)
    add(owner.legacy?.deviceId)
    add(group.digest)
    for (value in originals) {
        val conversation = value.conversation
        val room = requireNotNull(value.room)
        add(conversation.companyId)
        add(conversation.id)
        add(conversation.firstAccountId)
        add(conversation.secondAccountId)
        add(conversation.createdBy)
        add(conversation.createdAt)
        add(conversation.version)
        add(room.serverName)
        add(room.roomId)
        add(room.createdBy)
        add(room.boundAt)
    }
    return Result.Success(
        CollaborationSpaceReadScope(
            rooms.values.sortedBy { it.roomId },
            HexFormat.of().formatHex(digest.digest()),
        )
    )
}

private fun readScopeTime(at: Instant) =
    at.nano % 1000 == 0 && at.epochSecond in -62135596800L..253402300799L

private fun invalidReadScope() =
    Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_room_scope_invalid"))

private fun readScopeCapacity() =
    Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_sync_scope_capacity"))
