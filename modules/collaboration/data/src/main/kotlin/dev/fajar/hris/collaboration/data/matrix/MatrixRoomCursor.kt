package dev.fajar.hris.collaboration.data.matrix

import dev.fajar.hris.collaboration.domain.entities.CollaborationSyncOwner
import dev.fajar.hris.identity.data.crypto.IdentityKeyUnavailableException
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.util.concurrent.CancellationException
import javax.crypto.AEADBadTagException

enum class MatrixRoomCursorFailure {
    INVALID,
    RESET_REQUIRED,
    UNAVAILABLE,
}

class MatrixRoomCursorException(val reason: MatrixRoomCursorFailure) :
    RuntimeException("Room history cursor could not be processed")

/** Wire encoding only: the use case must authorize this exact owner and retained room. */
fun sealMatrixRoomCursor(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSyncOwner,
    roomId: String,
    position: String,
): String {
    if (!validPosition(position)) throw MatrixResponseException()
    interrupted()
    val purpose = purpose(crypto, owner, roomId)
    val sealed =
        try {
            crypto.encrypt(purpose, position)
        } catch (_: IdentityKeyUnavailableException) {
            throw MatrixRoomCursorException(MatrixRoomCursorFailure.UNAVAILABLE)
        }
    interrupted()
    return sealed
}

fun openMatrixRoomCursor(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSyncOwner,
    roomId: String,
    cursor: String,
): String {
    interrupted()
    val purpose = purpose(crypto, owner, roomId)
    val position =
        try {
            require(cursor.length in 1..4096 && cursor.all { it.code in 33..126 })
            crypto.decrypt(purpose, cursor).also { require(validPosition(it)) }
        } catch (error: Exception) {
            if (error is InterruptedException || error is CancellationException) throw error
            interrupted()
            when (error) {
                is IdentityKeyUnavailableException ->
                    throw MatrixRoomCursorException(MatrixRoomCursorFailure.RESET_REQUIRED)
                is IllegalArgumentException,
                is AEADBadTagException ->
                    throw MatrixRoomCursorException(MatrixRoomCursorFailure.INVALID)
                else -> throw error
            }
        }
    interrupted()
    return position
}

private fun validPosition(value: String) =
    value.length in 1..1024 && value.all { it.code in 33..126 }

private fun interrupted() {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
}

private fun purpose(
    crypto: IdentityTokenDataSource,
    owner: CollaborationSyncOwner,
    roomId: String,
): String {
    require(owner.serverName.length in 1..255 && owner.serverName.all { it.code in 33..126 })
    require(roomId.length in 2..255 && roomId.startsWith('!') && roomId.all { it.code in 33..126 })
    return "collaboration.room.history.v1:" +
        crypto.hash(
            "${owner.companyId}\n${owner.accountId}\n${owner.deviceId}\n${owner.serverName}\n$roomId"
        )
}
