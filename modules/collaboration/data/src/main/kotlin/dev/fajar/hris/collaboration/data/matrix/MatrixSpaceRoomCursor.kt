package dev.fajar.hris.collaboration.data.matrix

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource

fun sealMatrixSpaceRoomCursor(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceBoundRoom,
    position: String,
): String {
    checkedPublishedOwner(owner, room)
    return sealMatrixRoomCursorForPurpose(
        crypto,
        purpose(crypto, owner, room.binding.roomId),
        position,
    )
}

/** At most one exact original namespace retry, only after admitted LEGACY room/device transfer. */
fun openMatrixSpaceRoomCursor(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceBoundRoom,
    cursor: String,
): String {
    checkedPublishedOwner(owner, room)
    return open(
        crypto,
        owner,
        room.binding.roomId,
        collaborationSpaceLegacyHistoryOwner(owner, room),
        cursor,
    )
}

/** Same group coordinates before/after adoption; this does not prove current native admission. */
fun sealMatrixSpaceRoomCursor(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceLegacyConversation,
    position: String,
): String {
    val roomId = checkedOriginalRoomId(owner, room)
    return sealMatrixRoomCursorForPurpose(crypto, purpose(crypto, owner, roomId), position)
}

fun openMatrixSpaceRoomCursor(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceLegacyConversation,
    cursor: String,
): String {
    val roomId = checkedOriginalRoomId(owner, room)
    val original =
        owner.legacy?.let {
            CollaborationSyncOwner(it.companyId, owner.accountId, it.deviceId, owner.serverName)
        }
    return open(crypto, owner, roomId, original, cursor)
}

private fun open(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSpaceHistoryOwner,
    roomId: String,
    original: CollaborationSyncOwner?,
    cursor: String,
): String {
    try {
        return openMatrixRoomCursorForPurpose(crypto, purpose(crypto, owner, roomId), cursor)
    } catch (error: MatrixRoomCursorException) {
        if (error.reason != MatrixRoomCursorFailure.INVALID || original == null) throw error
        return openMatrixRoomCursor(crypto, original, roomId, cursor)
    }
}

private fun checkedPublishedOwner(
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceBoundRoom,
) {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    require(validCollaborationSpaceHistoryOwner(owner, room))
}

private fun checkedOriginalRoomId(
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceLegacyConversation,
): String {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    val binding = requireNotNull(room.room)
    // Validate immutable provenance here. The use case separately checks current time/admission.
    require(
        snapshotCollaborationSpaceOriginalReadRoom(owner, room, binding.boundAt) is Result.Success
    )
    return binding.roomId
}

private fun purpose(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSpaceHistoryOwner,
    roomId: String,
): String {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    return "collaboration.space.room.history.v1:" +
        crypto.hash(
            "${owner.spaceId}\n${owner.accountId}\n${owner.deviceId}\n${owner.serverName}\n${owner.matrixUserId}\n${owner.matrixDeviceId}\n$roomId"
        )
}
