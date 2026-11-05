package dev.fajar.hris.collaboration.domain.entities

/** Ciphertext only. The client owns content, logical message IDs and Megolm sessions. */
data class CollaborationRoomMessage(
    val transactionId: String,
    val ciphertext: String,
    val sessionId: String,
    val senderKey: String? = null,
    val deviceId: String? = null,
    /** Opaque reference only; it grants no access to the target event or another room. */
    val replyToEventId: String? = null,
    /** Replacement target only. The encrypted payload carries the new text and edit identity. */
    val replacesEventId: String? = null,
) {
    override fun toString() = "CollaborationRoomMessage(redacted)"
}
