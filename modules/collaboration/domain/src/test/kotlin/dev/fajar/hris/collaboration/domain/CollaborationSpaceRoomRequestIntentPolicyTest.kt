package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceRoomRequestIntentPolicyTest {
    private val legacy =
        CollaborationSpaceRoomOperation(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            UUID.randomUUID(),
            1,
            null,
            ClientVersion(ClientPlatform.ANDROID, 28),
        )

    @Test
    fun stableKeyIgnoresAuthenticationProofButBindsEveryBusinessInputAndKeepsLegacyNamespace() {
        val stable =
            collaborationSpaceRoomRequestKey(
                CollaborationSpaceRoomRequestFormat.STABLE_INTENT,
                legacy,
            )
        assertEquals("collaboration.space_room_setup.v2", stable.name)
        assertEquals(
            listOf(
                legacy.spaceId.toString(),
                legacy.conversationId.toString(),
                legacy.deviceId.toString(),
                "1",
                "initial",
                "initial",
            ),
            stable.payload,
        )
        val rotated =
            legacy.copy(
                nativeSessionId = UUID.randomUUID(),
                nativeSessionVersion = 8,
                client = ClientVersion(ClientPlatform.IOS, 40),
            )
        assertEquals(
            stable,
            collaborationSpaceRoomRequestKey(
                CollaborationSpaceRoomRequestFormat.STABLE_INTENT,
                rotated,
            ),
        )
        val original =
            collaborationSpaceRoomRequestKey(
                CollaborationSpaceRoomRequestFormat.NATIVE_PROOF,
                legacy,
            )
        assertEquals(collaborationSpaceRoomOperationKey(legacy), original)
        assertNotEquals(
            original,
            collaborationSpaceRoomRequestKey(
                CollaborationSpaceRoomRequestFormat.NATIVE_PROOF,
                rotated,
            ),
        )
        assertNotEquals(original.name, stable.name)
        for (changed in
            listOf(
                legacy.copy(spaceId = UUID.randomUUID()),
                legacy.copy(conversationId = UUID.randomUUID()),
                legacy.copy(deviceId = UUID.randomUUID()),
                legacy.copy(expectedDeviceVersion = 2),
                legacy.copy(
                    expectedAttempt = CollaborationSpaceRoomObservedAttempt(UUID.randomUUID(), 0)
                ),
            )) {
            assertNotEquals(
                stable,
                collaborationSpaceRoomRequestKey(
                    CollaborationSpaceRoomRequestFormat.STABLE_INTENT,
                    changed,
                ),
            )
        }
    }

    @Test
    fun invalidIntentCannotReachReceiptStorage() {
        val intent =
            CollaborationSpaceRoomRequestIntent(
                legacy.operationId,
                legacy.spaceId,
                legacy.conversationId,
                legacy.deviceId,
                1,
                null,
            )
        assertTrue(validCollaborationSpaceRoomReceiptDescriptor(intent))
        for (invalid in
            listOf(
                intent.copy(operationId = UUID(0, 0)),
                intent.copy(spaceId = UUID(0, 0)),
                intent.copy(conversationId = UUID(0, 0)),
                intent.copy(deviceId = UUID(0, 0)),
                intent.copy(expectedDeviceVersion = -1),
                intent.copy(expectedDeviceVersion = 1_000_000),
                intent.copy(expectedAttempt = CollaborationSpaceRoomObservedAttempt(UUID(0, 0), 0)),
                intent.copy(
                    expectedAttempt = CollaborationSpaceRoomObservedAttempt(UUID.randomUUID(), -1)
                ),
            )) {
            assertFalse(validCollaborationSpaceRoomReceiptDescriptor(invalid))
        }
    }
}
