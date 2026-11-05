package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.util.Base64

// Leave space for the provider's event envelope within Matrix's 65,536-byte event limit.
const val COLLABORATION_ROOM_CIPHERTEXT_LIMIT = 60_000

fun validateCollaborationRoomMessage(message: CollaborationRoomMessage): Result<Unit> =
    if (
        !Regex("[A-Za-z0-9._~-]{1,128}").matches(message.transactionId) ||
            message.ciphertext.length !in 1..COLLABORATION_ROOM_CIPHERTEXT_LIMIT ||
            decoded(message.ciphertext) == null ||
            decoded(message.sessionId, 32) == null ||
            message.senderKey?.let { decoded(it, 32) == null } == true ||
            message.deviceId?.let {
                it.length !in 1..128 || it.any { char -> char.code !in 33..126 }
            } == true ||
            message.replyToEventId?.let {
                it.length !in 2..255 ||
                    !it.startsWith('$') ||
                    it.any { char -> char.code !in 33..126 }
            } == true
    )
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_room_message"))
    else Result.Success(Unit)

fun collaborationRoomMessageMatchesDevice(
    message: CollaborationRoomMessage,
    keys: CollaborationMatrixDeviceKeys,
): Boolean {
    if (message.deviceId != null && message.deviceId != keys.deviceId) return false
    val provided = message.senderKey ?: return true
    val expected =
        decoded(keys.keys["curve25519:${keys.deviceId}"] ?: return false, 32) ?: return false
    return decoded(provided, 32)?.contentEquals(expected) == true
}

private fun decoded(value: String, size: Int? = null): ByteArray? {
    if (size != null && value.length !in 43..44) return null
    val bytes =
        try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            return null
        }
    if (bytes.isEmpty() || (size != null && bytes.size != size)) return null
    return bytes.takeIf {
        value == Base64.getEncoder().withoutPadding().encodeToString(bytes) ||
            value == Base64.getEncoder().encodeToString(bytes)
    }
}
