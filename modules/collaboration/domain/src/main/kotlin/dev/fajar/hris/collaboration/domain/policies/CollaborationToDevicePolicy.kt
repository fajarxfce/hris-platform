package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.util.Base64

fun snapshotCollaborationToDevice(request: CollaborationToDevice): Result<CollaborationToDevice> {
    if (
        !Regex("[A-Za-z0-9._~-]{1,128}").matches(request.transactionId) ||
            request.messages.size !in 1..100 ||
            request.messages.values.any { it.size !in 1..5 }
    )
        return invalidEnvelope()
    var characters = 0
    for (devices in request.messages.values) for (envelope in devices.values) {
        characters += envelope.body.length
        if (
            envelope.body.length !in 1..65536 ||
                characters > 524288 ||
                envelope.messageType !in 0..1 ||
                decoded(envelope.senderKey, 32) == null ||
                decoded(envelope.recipientKey, 32) == null ||
                decoded(envelope.body) == null ||
                envelope.messageId?.let {
                    it.length !in 1..128 || it.any { c -> c.code !in 33..126 }
                } == true
        )
            return invalidEnvelope()
    }
    val captured = request.messages.mapValues { it.value.toMap() }
    return snapshotCollaborationKeyQuery(
            CollaborationMatrixKeyQuery(captured.mapValues { it.value.keys.toList() })
        )
        .map { request.copy(messages = captured) }
}

/** Compare raw public keys; canonical padded and unpadded encodings identify the same device. */
fun collaborationEnvelopeMatchesKeys(
    envelope: CollaborationOlmEnvelope,
    sender: CollaborationMatrixDeviceKeys,
    recipient: CollaborationMatrixDeviceKeys,
): Boolean {
    val expectedSender =
        decoded(sender.keys["curve25519:${sender.deviceId}"] ?: return false, 32) ?: return false
    val expectedRecipient =
        decoded(recipient.keys["curve25519:${recipient.deviceId}"] ?: return false, 32)
            ?: return false
    return decoded(envelope.senderKey, 32)?.contentEquals(expectedSender) == true &&
        decoded(envelope.recipientKey, 32)?.contentEquals(expectedRecipient) == true
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

private fun invalidEnvelope() =
    Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_to_device"))
