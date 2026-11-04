package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.identity.data.crypto.IdentityKeyUnavailableException
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixSpaceRoomCursorTest {
    private val crypto = collaborationCursorFixture()

    @Test
    fun groupCursorBindsEveryOwnerCoordinateAndRejectsCompanyNamespaceReplay() {
        val f = SpaceRoomHistoryFixture()
        val owner = f.owner
        val room = f.room
        val cursor = sealMatrixSpaceRoomCursor(crypto, owner, room, "t100")
        assertEquals("t100", openMatrixSpaceRoomCursor(crypto, owner, room, cursor))
        val changedDevice = UUID.randomUUID()
        val changedSpace = UUID.randomUUID()
        val changedServer = "other.example.test"
        val newOwner =
            owner.copy(
                spaceId = changedSpace,
                matrixUserId =
                    "@${collaborationSpaceMatrixLocalpart(changedSpace, owner.accountId)}:${f.server}",
            )
        val otherSpace =
            room.copy(
                intent =
                    room.intent.copy(
                        spaceId = changedSpace,
                        firstMatrixUserId = newOwner.matrixUserId,
                        secondMatrixUserId =
                            "@${collaborationSpaceMatrixLocalpart(changedSpace, f.pair[1])}:${f.server}",
                    ),
                binding = room.binding.copy(spaceId = changedSpace),
            )
        val otherServer =
            room.copy(
                intent =
                    room.intent.copy(
                        serverName = changedServer,
                        firstMatrixUserId = f.user(f.pair[0]).replace(f.server, changedServer),
                        secondMatrixUserId = f.user(f.pair[1]).replace(f.server, changedServer),
                    )
            )
        for ((candidate, candidateRoom) in
            listOf(
                owner.copy(accountId = f.pair[1], matrixUserId = f.user(f.pair[1])) to room,
                owner.copy(
                    deviceId = changedDevice,
                    matrixDeviceId = "HRISG_${changedDevice.toString().replace("-", "")}",
                ) to room,
                owner to room.copy(binding = room.binding.copy(roomId = "!other:${f.server}")),
                newOwner to otherSpace,
                owner.copy(
                    serverName = changedServer,
                    matrixUserId = otherServer.intent.firstMatrixUserId,
                ) to otherServer,
            )) {
            assertTrue(validCollaborationSpaceHistoryOwner(candidate, candidateRoom))
            invalid { openMatrixSpaceRoomCursor(crypto, candidate, candidateRoom, cursor) }
        }
        // Identical UUID bytes cannot turn a company cursor into a group cursor.
        val companyOwner =
            CollaborationSyncOwner(f.space, owner.accountId, owner.deviceId, f.server)
        val companyCursor = sealMatrixRoomCursor(crypto, companyOwner, f.roomId, "t100")
        invalid { openMatrixSpaceRoomCursor(crypto, owner, room, companyCursor) }
        invalid { openMatrixRoomCursor(crypto, companyOwner, f.roomId, cursor) }
    }

    @Test
    fun onlyExactRetainedRoomAndOriginalDeviceCanBridgeOldCompanyCursors() {
        val f = SpaceRoomHistoryFixture(legacy = true)
        val original = requireNotNull(collaborationSpaceLegacyHistoryOwner(f.owner, f.room))
        assertEquals(f.company, original.companyId)
        assertEquals(f.originalDevice, original.deviceId)
        assertNotEquals(f.owner.deviceId, original.deviceId)
        val old = sealMatrixRoomCursor(crypto, original, f.roomId, "t91")
        assertEquals("t91", openMatrixSpaceRoomCursor(crypto, f.owner, f.room, old))
        val current = sealMatrixSpaceRoomCursor(crypto, f.owner, f.room, "t90")
        assertEquals("t90", openMatrixSpaceRoomCursor(crypto, f.owner, f.room, current))
        invalid { openMatrixRoomCursor(crypto, original, f.roomId, current) }
        val newRoom =
            f.room.copy(
                intent =
                    f.room.intent.copy(origin = CollaborationSpaceRoomOrigin.SPACE, original = null)
            )
        assertTrue(validCollaborationSpaceHistoryOwner(f.owner, newRoom))
        assertNull(collaborationSpaceLegacyHistoryOwner(f.owner, newRoom))
        invalid { openMatrixSpaceRoomCursor(crypto, f.owner, newRoom, old) }
        val anotherDevice = UUID.randomUUID()
        val changed =
            f.owner.copy(
                legacy = CollaborationLegacyDevicePosition(f.company, anotherDevice),
                matrixDeviceId = "HRIS_${anotherDevice.toString().replace("-", "")}",
            )
        assertTrue(validCollaborationSpaceHistoryOwner(changed, f.room))
        invalid { openMatrixSpaceRoomCursor(crypto, changed, f.room, old) }
        assertFalse(validCollaborationSpaceHistoryOwner(f.owner.copy(legacy = null), f.room))
        assertFalse(
            validCollaborationSpaceHistoryOwner(
                f.owner.copy(matrixDeviceId = "HRISG_${f.device.toString().replace("-", "")}"),
                f.room,
            )
        )
        for (owner in
            listOf(
                original.copy(companyId = UUID.randomUUID()),
                original.copy(accountId = f.pair[1]),
            )) {
            val foreign = sealMatrixRoomCursor(crypto, owner, f.roomId, "t91")
            invalid { openMatrixSpaceRoomCursor(crypto, f.owner, f.room, foreign) }
        }
    }

    @Test
    fun missingKeysAndCancellationDoNotTriggerLegacyFallbackOrLoseClassification() {
        val f = SpaceRoomHistoryFixture(legacy = true)
        var calls = 0
        var failure: Exception = IdentityKeyUnavailableException()
        val unavailable =
            object : IdentityTokenDataSource by crypto {
                override fun decrypt(purpose: String, value: String): String {
                    calls++
                    throw failure
                }

                override fun encrypt(purpose: String, value: String): String = throw failure
            }
        assertEquals(
            MatrixRoomCursorFailure.RESET_REQUIRED,
            assertThrows(MatrixRoomCursorException::class.java) {
                    openMatrixSpaceRoomCursor(unavailable, f.owner, f.room, "fixture")
                }
                .reason,
        )
        assertEquals(1, calls)
        assertEquals(
            MatrixRoomCursorFailure.UNAVAILABLE,
            assertThrows(MatrixRoomCursorException::class.java) {
                    sealMatrixSpaceRoomCursor(unavailable, f.owner, f.room, "t10")
                }
                .reason,
        )
        failure = CancellationException("fictional cancelled cursor")
        assertThrows(CancellationException::class.java) {
            openMatrixSpaceRoomCursor(unavailable, f.owner, f.room, "fixture")
        }
        assertEquals(2, calls)
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedException::class.java) {
                openMatrixSpaceRoomCursor(unavailable, f.owner, f.room, "fixture")
            }
            assertEquals(2, calls)
        } finally {
            Thread.interrupted()
        }
    }

    private fun invalid(operation: () -> String) {
        assertEquals(
            MatrixRoomCursorFailure.INVALID,
            assertThrows(MatrixRoomCursorException::class.java) { operation() }.reason,
        )
    }
}
