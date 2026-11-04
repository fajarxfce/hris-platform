package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

/** DIRECT ownership only; current native/device authority and provider access are separate. */
fun snapshotCollaborationSpaceRoomScope(
    spaceId: UUID,
    accountId: UUID,
    serverName: String,
    acquired: List<CollaborationSpaceBoundRoom>,
    at: Instant,
): Result<CollaborationSpaceRoomScope> {
    if (acquired.size > COLLABORATION_SYNC_ROOM_LIMIT)
        return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_sync_scope_capacity"))
    if (
        spaceId == UUID(0, 0) ||
            accountId == UUID(0, 0) ||
            !validCollaborationSpaceServer(serverName)
    )
        return invalidSpaceRoomScope()
    val rooms = acquired.toList().sortedBy { it.intent.conversationId.toString() }
    if (
        rooms.map { it.intent.conversationId }.toSet().size != rooms.size ||
            rooms.map { it.binding.roomId }.toSet().size != rooms.size
    )
        return invalidSpaceRoomScope()
    val identities = mutableMapOf<UUID, String>()
    for (room in rooms) {
        val valid =
            snapshotCollaborationSpaceBoundRoom(
                spaceId,
                accountId,
                serverName,
                room.binding.roomId,
                room,
                at,
            )
        if (valid is Result.Failed) return valid
        for ((account, user) in
            listOf(
                room.intent.firstAccountId to room.intent.firstMatrixUserId,
                room.intent.secondAccountId to room.intent.secondMatrixUserId,
            )) {
            val prior = identities.putIfAbsent(account, user)
            if (prior != null && prior != user) return invalidSpaceRoomScope()
        }
    }
    val digest = MessageDigest.getInstance("SHA-256")
    fun add(value: Any?) {
        digest.update((value?.toString() ?: "").toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
    }
    add("organization-direct-sync-v1-receipts")
    add(spaceId)
    add(accountId)
    add(serverName)
    for (room in rooms) {
        val intent = room.intent
        val binding = room.binding
        add(intent.conversationId)
        add(intent.firstAccountId)
        add(intent.secondAccountId)
        add(intent.firstMatrixUserId)
        add(intent.secondMatrixUserId)
        add(intent.requestedBy)
        add(intent.matrixCreatorAccountId)
        add(intent.origin)
        add(intent.original?.companyId)
        add(intent.original?.conversationId)
        add(intent.original?.roomId)
        add(intent.original?.boundAt)
        add(intent.createdAt)
        add(binding.roomId)
        add(binding.publishedBy)
        add(binding.requestRevision)
        add(binding.leaseToken)
        add(binding.createdAt)
    }
    return Result.Success(
        CollaborationSpaceRoomScope(rooms, HexFormat.of().formatHex(digest.digest()))
    )
}

private fun invalidSpaceRoomScope() =
    Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_room_scope_invalid"))
