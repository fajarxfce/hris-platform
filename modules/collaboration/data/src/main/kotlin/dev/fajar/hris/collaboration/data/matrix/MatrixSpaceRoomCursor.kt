package dev.fajar.hris.collaboration.data.matrix

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource

fun sealMatrixSpaceRoomCursor(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceBoundRoom,
    position: String,
): String = sealMatrixRoomCursorForPurpose(crypto, purpose(crypto, owner, room), position)

/** At most one exact original namespace retry, only after admitted LEGACY room/device transfer. */
fun openMatrixSpaceRoomCursor(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceBoundRoom,
    cursor: String,
): String {
    val purpose = purpose(crypto, owner, room)
    try {
        return openMatrixRoomCursorForPurpose(crypto, purpose, cursor)
    } catch (error: MatrixRoomCursorException) {
        if (error.reason != MatrixRoomCursorFailure.INVALID) throw error
        val original = collaborationSpaceLegacyHistoryOwner(owner, room) ?: throw error
        return openMatrixRoomCursor(crypto, original, room.binding.roomId, cursor)
    }
}

private fun purpose(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSpaceHistoryOwner,
    room: CollaborationSpaceBoundRoom,
): String {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    require(validCollaborationSpaceHistoryOwner(owner, room))
    return "collaboration.space.room.history.v1:" +
        crypto.hash(
            "${owner.spaceId}\n${owner.accountId}\n${owner.deviceId}\n${owner.serverName}\n${owner.matrixUserId}\n${owner.matrixDeviceId}\n${room.binding.roomId}"
        )
}
