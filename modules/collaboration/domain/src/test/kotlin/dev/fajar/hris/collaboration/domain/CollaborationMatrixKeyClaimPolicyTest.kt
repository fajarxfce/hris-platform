package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyClaim
import dev.fajar.hris.collaboration.domain.policies.snapshotCollaborationKeyClaim
import dev.fajar.hris.core.domain.Result
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationMatrixKeyClaimPolicyTest {
    private val device = "HRIS_" + "0".repeat(32)

    @Test
    fun explicitSignedSelectionIsDetachedFromCallerMutations() {
        val devices = mutableMapOf(device to "signed_curve25519")
        val users = mutableMapOf("@fixture:example.test" to devices)
        val result =
            (snapshotCollaborationKeyClaim(CollaborationMatrixKeyClaim(users)) as Result.Success)
                .value
        devices.clear()
        users.clear()
        assertEquals(
            mapOf("@fixture:example.test" to mapOf(device to "signed_curve25519")),
            result.oneTimeKeys,
        )
    }

    @Test
    fun wildcardsUnsignedAlgorithmsAndOversizedBatchesAreRejected() {
        for (devices in
            listOf(
                emptyMap(),
                mapOf("*" to "signed_curve25519"),
                mapOf(device to "curve25519"),
                mapOf(device to ""),
                (0..5).associate {
                    "HRIS_" + it.toString().padStart(32, '0') to "signed_curve25519"
                },
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                snapshotCollaborationKeyClaim(
                    CollaborationMatrixKeyClaim(mapOf("@fixture:example.test" to devices))
                ),
            )
        }
        for (users in
            listOf(
                emptyMap(),
                (0..100).associate {
                    "@fixture$it:example.test" to mapOf(device to "signed_curve25519")
                },
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                snapshotCollaborationKeyClaim(CollaborationMatrixKeyClaim(users)),
            )
        }
    }
}
