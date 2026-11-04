package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.util.Base64
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceToDevicePolicyTest {
    private val groupDevice = "HRISG_" + "1".repeat(32)
    private val retainedDevice = "HRIS_" + "2".repeat(32)
    private val user = "@fixture:matrix.example.test"
    private val key = Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 })
    private val envelope = CollaborationOlmEnvelope(key, key, "Y2lwaGVy", 0, "original-sdk-message")

    @Test
    fun groupCaptureDetachesBothNamespacesAndPreservesOriginalSdkTransactionAndMessageIds() {
        val devices = mutableMapOf(groupDevice to envelope, retainedDevice to envelope)
        val messages = mutableMapOf(user to devices)
        val request = CollaborationToDevice("original_sdk-txn.123~", messages)
        val captured = (snapshotCollaborationSpaceToDevice(request) as Result.Success).value
        devices.clear()
        messages.clear()
        assertEquals("original_sdk-txn.123~", captured.transactionId)
        assertEquals(
            mapOf(groupDevice to envelope, retainedDevice to envelope),
            captured.messages.getValue(user),
        )
    }

    @Test
    fun companyPolicyStillRejectsGroupIdsWhileRetainedDeviceShapeWorksInBothWrappers() {
        val group = CollaborationToDevice("same-txn", mapOf(user to mapOf(groupDevice to envelope)))
        assertInstanceOf(Result.Failed::class.java, snapshotCollaborationToDevice(group))
        assertInstanceOf(Result.Success::class.java, snapshotCollaborationSpaceToDevice(group))
        val retained = group.copy(messages = mapOf(user to mapOf(retainedDevice to envelope)))
        assertEquals(
            snapshotCollaborationToDevice(retained),
            snapshotCollaborationSpaceToDevice(retained),
        )
    }

    @Test
    fun groupedFramesEnforceEveryExistingCiphertextAndAggregateBudget() {
        val request =
            CollaborationToDevice("same-txn", mapOf(user to mapOf(groupDevice to envelope)))
        for (invalid in
            listOf(
                envelope.copy(body = "*"),
                envelope.copy(body = ""),
                envelope.copy(body = "A".repeat(65537)),
                envelope.copy(senderKey = "x"),
                envelope.copy(recipientKey = "x"),
                envelope.copy(messageType = 2),
                envelope.copy(messageId = "\n"),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                snapshotCollaborationSpaceToDevice(
                    request.copy(messages = mapOf(user to mapOf(groupDevice to invalid)))
                ),
            )
        }
        for (ids in
            listOf(
                emptyMap(),
                mapOf("*" to envelope),
                (1..6).associate { "HRISG_" + it.toString().padStart(32, '0') to envelope },
            )) assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationSpaceToDevice(request.copy(messages = mapOf(user to ids))),
        )
        val eight =
            (1..8).associate {
                "@user$it:matrix.example.test" to
                    mapOf(groupDevice to envelope.copy(body = "A".repeat(65536)))
            }
        assertInstanceOf(
            Result.Success::class.java,
            snapshotCollaborationSpaceToDevice(request.copy(messages = eight)),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationSpaceToDevice(
                request.copy(
                    messages = eight + ("@overflow:matrix.example.test" to eight.values.first())
                )
            ),
        )
        for (transaction in listOf("", "../other", "A".repeat(129))) assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationSpaceToDevice(request.copy(transactionId = transaction)),
        )
    }
}
