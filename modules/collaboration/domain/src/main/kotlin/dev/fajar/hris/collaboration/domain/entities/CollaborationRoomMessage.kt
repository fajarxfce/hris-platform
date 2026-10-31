package dev.fajar.hris.collaboration.domain.entities

/** Ciphertext only. The client owns content, logical message IDs and Megolm sessions. */
data class CollaborationRoomMessage(
    val transactionId: String,
    val ciphertext: String,
    val sessionId: String,
    val senderKey: String? = null,
    val deviceId: String? = null,
) {
    override fun toString() = "CollaborationRoomMessage(redacted)"
}
