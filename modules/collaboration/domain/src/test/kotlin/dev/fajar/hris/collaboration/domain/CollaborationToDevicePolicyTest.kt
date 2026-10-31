package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.util.Base64
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationToDevicePolicyTest {
    private val device = "HRIS_" + "0".repeat(32)
    private val user = "@fixture:example.test"
    private val key = Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 })
    private val envelope = CollaborationOlmEnvelope(key, key, "Y2lwaGVy", 0, "fixture-message")

    private fun request(value: CollaborationOlmEnvelope = envelope) =
        CollaborationToDevice("fixture-txn", mapOf(user to mapOf(device to value)))

    @Test
    fun envelopeSnapshotPreservesOpaqueFieldsAndDetachesSelections() {
        val devices = mutableMapOf(device to envelope)
        val users = mutableMapOf(user to devices)
        val captured =
            (snapshotCollaborationToDevice(CollaborationToDevice("fixture-txn", users))
                    as Result.Success)
                .value
        devices.clear()
        users.clear()
        assertEquals(request(), captured)
    }

    @Test
    fun malformedKeysCiphertextTypesAndPayloadBudgetsAreRejected() {
        for (value in
            listOf(
                envelope.copy(body = "*"),
                envelope.copy(body = ""),
                envelope.copy(body = "A".repeat(65537)),
                envelope.copy(senderKey = "x"),
                envelope.copy(messageType = 2),
                envelope.copy(messageId = "\n"),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                snapshotCollaborationToDevice(request(value)),
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationToDevice(request().copy(transactionId = "../other")),
        )
        val many =
            (1..9).associate {
                "@user$it:example.test" to mapOf(device to envelope.copy(body = "A".repeat(65536)))
            }
        assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationToDevice(CollaborationToDevice("fixture", many)),
        )
    }

    @Test
    fun bothRawCurveKeysMustMatchValidatedIdentities() {
        val identity =
            CollaborationMatrixDeviceKeys(
                user,
                device,
                emptyList(),
                mapOf("curve25519:$device" to key),
                emptyMap(),
            )
        assertTrue(
            collaborationEnvelopeMatchesKeys(
                envelope.copy(senderKey = key + "="),
                identity,
                identity,
            )
        )
        val other =
            identity.copy(
                keys =
                    mapOf(
                        "curve25519:$device" to
                            Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
                    )
            )
        assertFalse(collaborationEnvelopeMatchesKeys(envelope, other, identity))
        assertFalse(collaborationEnvelopeMatchesKeys(envelope, identity, other))
    }
}
