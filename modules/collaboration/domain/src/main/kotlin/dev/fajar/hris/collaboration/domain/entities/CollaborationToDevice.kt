package dev.fajar.hris.collaboration.domain.entities

/** Opaque Olm ciphertext; participant plaintext and private keys never enter this contract. */
data class CollaborationOlmEnvelope(
    val senderKey: String,
    val recipientKey: String,
    val body: String,
    val messageType: Int,
    val messageId: String? = null,
) {
    override fun toString() = "CollaborationOlmEnvelope(<redacted>)"
}

data class CollaborationToDevice(
    val transactionId: String,
    val messages: Map<String, Map<String, CollaborationOlmEnvelope>>,
) {
    override fun toString() = "CollaborationToDevice(<redacted>)"
}
