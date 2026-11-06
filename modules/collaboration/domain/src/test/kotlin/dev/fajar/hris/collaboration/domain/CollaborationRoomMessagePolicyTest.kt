package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.util.Base64
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationRoomMessagePolicyTest {
    private val key = Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 3 })
    private val message =
        CollaborationRoomMessage("stable-transaction", "Y2lwaGVydGV4dA", key, key, "HRIS_fixture")

    @Test
    fun canonicalCiphertextAndSessionKeysAreBoundedWithoutExposingPayloadInDiagnostics() {
        assertEquals(Result.Success(Unit), validateCollaborationRoomMessage(message))
        assertEquals(
            Result.Success(Unit),
            validateCollaborationRoomMessage(message.copy(ciphertext = "A".repeat(60_000))),
        )
        for (invalid in
            listOf(
                message.copy(transactionId = "../foreign"),
                message.copy(transactionId = "x".repeat(129)),
                message.copy(ciphertext = "A".repeat(60_004)),
                message.copy(ciphertext = "YQ\n"),
                message.copy(ciphertext = "YR=="),
                message.copy(sessionId = "YQ"),
                message.copy(senderKey = "YQ"),
                message.copy(deviceId = "bad\n"),
            )) assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationRoomMessage(invalid),
        )
        assertFalse(message.toString().contains(message.ciphertext))
    }

    @Test
    fun replyReferencesAreBoundedOpaqueEventIdsAndNeverExpandTheMessageAuthority() {
        for (target in listOf("\$original", "\$" + "x".repeat(254))) {
            val reply = message.copy(replyToEventId = target)
            assertEquals(Result.Success(Unit), validateCollaborationRoomMessage(reply))
            assertFalse(reply.toString().contains(target))
        }
        for (target in
            listOf(
                "",
                "\$",
                "not-event",
                "\$with space",
                "\$line\n",
                "\$nul\u0000",
                "\$" + "x".repeat(255),
                "\$é",
            )) assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationRoomMessage(message.copy(replyToEventId = target)),
        )
    }

    @Test
    fun deprecatedSenderMetadataWhenPresentMustMatchThePinnedDevice() {
        val keys =
            CollaborationMatrixDeviceKeys(
                "@fixture:proof.invalid",
                "HRIS_fixture",
                listOf("m.megolm.v1.aes-sha2"),
                mapOf("curve25519:HRIS_fixture" to "$key="),
                emptyMap(),
            )
        assertTrue(collaborationRoomMessageMatchesDevice(message, keys))
        assertTrue(
            collaborationRoomMessageMatchesDevice(
                message.copy(senderKey = null, deviceId = null),
                keys,
            )
        )
        assertFalse(collaborationRoomMessageMatchesDevice(message.copy(deviceId = "other"), keys))
        assertFalse(
            collaborationRoomMessageMatchesDevice(
                message.copy(senderKey = Base64.getEncoder().encodeToString(ByteArray(32) { 4 })),
                keys,
            )
        )
    }

    @Test
    fun replacementReferencesShareBoundsAndAreMutuallyExclusiveWithReplies() {
        for (target in listOf("\$original", "\$" + "x".repeat(254))) {
            assertEquals(
                Result.Success(Unit),
                validateCollaborationRoomMessage(message.copy(replacesEventId = target)),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                validateCollaborationRoomMessage(
                    message.copy(replacesEventId = target, replyToEventId = target)
                ),
            )
        }
        for (target in
            listOf(
                "",
                "\$",
                "plain",
                "\$space here",
                "\$line\n",
                "\$nul\u0000",
                "\$é",
                "\$" + "x".repeat(255),
            )) assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationRoomMessage(message.copy(replacesEventId = target)),
        )
    }
}
