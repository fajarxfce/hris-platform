package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

const val COLLABORATION_SYNC_ROOM_LIMIT = 10_000

/** This append-only DIRECT scope must not be reused for removable group/context membership. */
fun snapshotCollaborationRoomScope(
    companyId: UUID,
    accountId: UUID,
    serverName: String,
    acquired: List<CollaborationOwnedRoom>,
): Result<CollaborationRoomScope> {
    if (acquired.size > COLLABORATION_SYNC_ROOM_LIMIT)
        return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_sync_scope_capacity"))
    val rooms = acquired.toList().sortedBy { it.conversationId.toString() }
    if (
        rooms.any {
            it.companyId != companyId ||
                it.serverName != serverName ||
                it.firstAccountId == it.secondAccountId ||
                accountId !in setOf(it.firstAccountId, it.secondAccountId) ||
                it.creatorAccountId !in setOf(it.firstAccountId, it.secondAccountId) ||
                it.roomId.length !in 2..255 ||
                !it.roomId.startsWith('!') ||
                it.roomId.any { char -> char.code !in 33..126 }
        } ||
            rooms.map { it.conversationId }.toSet().size != rooms.size ||
            rooms.map { it.roomId }.toSet().size != rooms.size
    )
        return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_room_scope_invalid"))
    val digest = MessageDigest.getInstance("SHA-256")
    fun add(value: Any) {
        digest.update(value.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
    }
    add("direct-sync-v1")
    add(companyId)
    add(accountId)
    add(serverName)
    for (room in rooms) {
        add(room.conversationId)
        add(room.firstAccountId)
        add(room.secondAccountId)
        add(room.creatorAccountId)
        add(room.roomId)
    }
    return Result.Success(CollaborationRoomScope(rooms, HexFormat.of().formatHex(digest.digest())))
}
