package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationDirectMetadataPolicyTest {
    @Test
    fun projectionUsesTheCounterpartAndCanonicalRoomOrdering() {
        val owner =
            CollaborationSyncOwner(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "proof.invalid",
            )
        val peer = UUID.randomUUID()
        val room =
            CollaborationOwnedRoom(
                owner.companyId,
                UUID.randomUUID(),
                owner.accountId,
                peer,
                peer,
                owner.serverName,
                "!z",
            )
        val second =
            room.copy(
                conversationId = UUID.randomUUID(),
                firstAccountId = peer,
                secondAccountId = owner.accountId,
                roomId = "!a",
            )
        val scope =
            (snapshotCollaborationRoomScope(
                    owner.companyId,
                    owner.accountId,
                    owner.serverName,
                    listOf(room, second),
                )
                    as Result.Success)
                .value
        assertEquals(
            mapOf(
                "@${collaborationMatrixLocalpart(owner.companyId, peer)}:proof.invalid" to
                    listOf("!a", "!z")
            ),
            collaborationDirectMetadata(owner, scope),
        )
    }

    @Test
    fun normalizationCopiesListsAndTreatsRoomOrderAsSetOrder() {
        val rooms = mutableListOf("!z", "!a")
        val value = mutableMapOf("@peer:proof.invalid" to rooms)
        val copy = (normalizeCollaborationDirectMetadata(value) as Result.Success).value
        rooms.clear()
        value.clear()
        assertEquals(mapOf("@peer:proof.invalid" to listOf("!a", "!z")), copy)
        assertEquals(
            Result.Success(emptyMap<String, List<String>>()),
            normalizeCollaborationDirectMetadata(emptyMap()),
        )
    }

    @Test
    fun malformedAndOverCapacityConfirmationIsRejectedBeforeAcquisition() {
        for (value in
            listOf(
                mapOf("peer" to listOf("!room")),
                mapOf("@peer\n" to listOf("!room")),
                mapOf("@peer" to emptyList()),
                mapOf("@peer" to listOf("!room", "!room")),
                mapOf("@peer" to listOf("room")),
                mapOf("@peer" to listOf("!bad\n")),
                mapOf("@peer" to List(COLLABORATION_SYNC_ROOM_LIMIT + 1) { "!$it" }),
            )) assertInstanceOf(
            Result.Failed::class.java,
            normalizeCollaborationDirectMetadata(value),
        )
    }
}
