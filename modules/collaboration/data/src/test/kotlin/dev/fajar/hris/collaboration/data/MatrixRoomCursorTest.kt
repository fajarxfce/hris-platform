package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationSyncCursorRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.crypto.IdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixRoomCursorTest {
    private val owner =
        CollaborationSyncOwner(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "proof.invalid",
        )
    private val crypto = collaborationCursorFixture()
    private val room = "!fixture"

    @Test
    fun roomPositionIsOpaqueNondeterministicAndCannotCrossRoomOwnerOrCursorPurpose() {
        val cursor = sealMatrixRoomCursor(crypto, owner, room, "t5-42")
        assertEquals("t5-42", openMatrixRoomCursor(crypto, owner, room, cursor))
        assertFalse(cursor.contains("t5-42"))
        assertNotEquals(cursor, sealMatrixRoomCursor(crypto, owner, room, "t5-42"))
        for (other in
            listOf(
                owner.copy(companyId = UUID.randomUUID()),
                owner.copy(accountId = UUID.randomUUID()),
                owner.copy(deviceId = UUID.randomUUID()),
                owner.copy(serverName = "other.invalid"),
            )) {
            assertEquals(
                MatrixRoomCursorFailure.INVALID,
                assertThrows(MatrixRoomCursorException::class.java) {
                        openMatrixRoomCursor(crypto, other, room, cursor)
                    }
                    .reason,
            )
        }
        assertEquals(
            MatrixRoomCursorFailure.INVALID,
            assertThrows(MatrixRoomCursorException::class.java) {
                    openMatrixRoomCursor(crypto, owner, "!other", cursor)
                }
                .reason,
        )
        val sync = EncryptedCollaborationSyncCursorRepository(crypto)
        val syncCursor =
            (sync.seal(owner, CollaborationSyncPosition("a".repeat(64), "s42", "0"))
                    as Result.Success)
                .value
        assertEquals(
            MatrixRoomCursorFailure.INVALID,
            assertThrows(MatrixRoomCursorException::class.java) {
                    openMatrixRoomCursor(crypto, owner, room, syncCursor)
                }
                .reason,
        )
        assertEquals(
            "invalid_collaboration_sync_cursor",
            (sync.open(owner, cursor) as Result.Failed).failure.code,
        )
    }

    @Test
    fun retiredKeyRequiresReanchoringWhileMalformedPositionsAndInterruptionStayDistinct() {
        val cursor = sealMatrixRoomCursor(crypto, owner, room, "t5-42")
        val next = SecretKeySpec(ByteArray(32) { 18 }, "AES")
        val rotated =
            JceIdentityTokenDataSource(
                IdentityKeyring(
                    "next",
                    mapOf("next" to next, "fixture" to SecretKeySpec(ByteArray(32) { 17 }, "AES")),
                )
            )
        assertEquals("t5-42", openMatrixRoomCursor(rotated, owner, room, cursor))
        val retired = JceIdentityTokenDataSource(IdentityKeyring("next", mapOf("next" to next)))
        val result =
            safeMatrixProviderCall { openMatrixRoomCursor(retired, owner, room, cursor) }
                as Result.Failed
        assertEquals("collaboration_history_reset_required", result.failure.code)
        for (bad in listOf("", "t5-42", "x".repeat(4097), cursor + "\n")) assertThrows(
            MatrixRoomCursorException::class.java
        ) {
            openMatrixRoomCursor(crypto, owner, room, bad)
        }
        for (bad in listOf("", "x".repeat(1025), "t5\n42")) assertThrows(
            MatrixResponseException::class.java
        ) {
            sealMatrixRoomCursor(crypto, owner, room, bad)
        }
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                sealMatrixRoomCursor(crypto, owner, room, "t5-42")
            }
        } finally {
            Thread.interrupted()
        }
    }
}
