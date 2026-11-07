package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.OperationKey
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceRoomOperationPolicyTest {
    private fun id(value: Long) = UUID(0, value)

    private val original =
        CollaborationSpaceRoomOperation(
            id(1),
            id(2),
            id(3),
            id(4),
            7,
            id(5),
            9,
            null,
            ClientVersion(ClientPlatform.ANDROID, 12),
        )

    @Test
    fun persistedFingerprintKeepsOriginalFieldOrderAndBothInitialSentinels() {
        assertTrue(validCollaborationSpaceRoomOperation(original))
        assertEquals(
            OperationKey(
                "collaboration.space_room_setup",
                id(1),
                listOf(
                    id(2).toString(),
                    id(3).toString(),
                    id(4).toString(),
                    "7",
                    id(5).toString(),
                    "9",
                    "initial",
                    "initial",
                    "ANDROID",
                    "12",
                ),
            ),
            collaborationSpaceRoomOperationKey(original),
        )
    }

    @Test
    fun aTerminalRetryKeepsTheObservedAccountWithItsAccountLocalRevision() {
        val retry =
            original.copy(expectedAttempt = CollaborationSpaceRoomObservedAttempt(id(6), 999999))
        assertTrue(validCollaborationSpaceRoomOperation(retry))
        val key = collaborationSpaceRoomOperationKey(retry)
        assertEquals(listOf("999999", id(6).toString()), key.payload.subList(6, 8))
        assertNotEquals(
            key,
            collaborationSpaceRoomOperationKey(
                retry.copy(expectedAttempt = CollaborationSpaceRoomObservedAttempt(id(7), 999999))
            ),
        )
        assertNotEquals(
            key,
            collaborationSpaceRoomOperationKey(retry.copy(nativeSessionVersion = 8)),
        )
        assertNotEquals(
            key,
            collaborationSpaceRoomOperationKey(retry.copy(client = retry.client.copy(build = 13))),
        )
    }
}
