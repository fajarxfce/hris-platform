package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationRoomScopePolicyTest {
    private val company = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val peer = UUID.randomUUID()

    private fun room() =
        CollaborationOwnedRoom(
            company,
            UUID.randomUUID(),
            account,
            peer,
            account,
            "proof.invalid",
            "!${UUID.randomUUID()}",
        )

    private fun scope(rooms: List<CollaborationOwnedRoom>) =
        snapshotCollaborationRoomScope(company, account, "proof.invalid", rooms)

    @Test
    fun stableDetachedSnapshotDetectsNewRoomsAndRoomBindingChanges() {
        val first = room()
        val second = room()
        val input = mutableListOf(second, first)
        val captured = (scope(input) as Result.Success).value
        input.clear()
        assertEquals(captured, (scope(listOf(first, second)) as Result.Success).value)
        assertNotEquals(captured.digest, (scope(listOf(first)) as Result.Success).value.digest)
        assertNotEquals(
            captured.digest,
            (scope(listOf(first, second.copy(roomId = "!replacement"))) as Result.Success)
                .value
                .digest,
        )
        assertNotEquals(
            captured.digest,
            (snapshotCollaborationRoomScope(company, peer, "proof.invalid", captured.rooms)
                    as Result.Success)
                .value
                .digest,
        )
        assertEquals(2, captured.rooms.size)
    }

    @Test
    fun foreignAndAmbiguousProjectionIsNeverSilentlyFiltered() {
        val valid = room()
        for (bad in
            listOf(
                valid.copy(companyId = UUID.randomUUID()),
                valid.copy(firstAccountId = UUID.randomUUID()),
                valid.copy(secondAccountId = account),
                valid.copy(creatorAccountId = UUID.randomUUID()),
                valid.copy(serverName = "other.invalid"),
                valid.copy(roomId = "!bad\nroom"),
            )) assertInstanceOf(Result.Failed::class.java, scope(listOf(bad)))
        assertInstanceOf(Result.Failed::class.java, scope(listOf(valid, valid)))
        assertInstanceOf(
            Result.Failed::class.java,
            scope(listOf(valid, valid.copy(conversationId = UUID.randomUUID()))),
        )
    }

    @Test
    fun capacityFailsInsteadOfLosingAContinuationRoom() {
        val result = scope(List(COLLABORATION_SYNC_ROOM_LIMIT + 1) { room() }) as Result.Failed
        assertEquals("collaboration_sync_scope_capacity", result.failure.code)
        assertEquals(
            emptyList<CollaborationOwnedRoom>(),
            (scope(emptyList()) as Result.Success).value.rooms,
        )
    }
}
