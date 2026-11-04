package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceBoundRoomPolicyTest {
    private val space = UUID.randomUUID()
    private val pair = listOf(UUID.randomUUID(), UUID.randomUUID()).sortedBy(UUID::toString)
    private val conversation = UUID.randomUUID()
    private val server = "matrix.example.test"
    private val room = "!original-room:$server"
    private val at = Instant.parse("2026-11-04T12:00:00Z")
    private val intent =
        CollaborationSpaceRoomIntent(
            space,
            conversation,
            pair[0],
            pair[1],
            pair[0],
            pair[0],
            server,
            user(pair[0]),
            user(pair[1]),
            CollaborationSpaceRoomOrigin.SPACE,
            null,
            at.minusSeconds(60),
        )
    private val binding =
        CollaborationSpaceRoomBinding(
            space,
            conversation,
            pair[1],
            0,
            UUID.randomUUID(),
            room,
            at.minusSeconds(30),
            pair[0],
        )
    private val bound = CollaborationSpaceBoundRoom(intent, binding)

    private fun user(account: UUID) =
        "@${collaborationSpaceMatrixLocalpart(space, account)}:$server"

    private fun snapshot(value: CollaborationSpaceBoundRoom?) =
        snapshotCollaborationSpaceBoundRoom(space, pair[0], server, room, value, at)

    @Test
    fun originalSpaceAndLegacyCoordinatesRemainDistinctWithoutReconstruction() {
        assertEquals(Result.Success(bound), snapshot(bound))
        val company = UUID.randomUUID()
        fun legacy(account: UUID) = "@${collaborationMatrixLocalpart(company, account)}:$server"
        val mixed = bound.copy(intent = intent.copy(secondMatrixUserId = legacy(pair[1])))
        assertEquals(Result.Success(mixed), snapshot(mixed))
        val original =
            bound.copy(
                intent =
                    intent.copy(
                        origin = CollaborationSpaceRoomOrigin.LEGACY,
                        firstMatrixUserId = legacy(pair[0]),
                        secondMatrixUserId = legacy(pair[1]),
                        original =
                            CollaborationSpaceOriginalRoom(
                                company,
                                UUID.randomUUID(),
                                room,
                                at.minusSeconds(120),
                            ),
                    )
            )
        assertEquals(Result.Success(original), snapshot(original))
        assertInstanceOf(
            Result.Failed::class.java,
            snapshot(original.copy(binding = binding.copy(roomId = "!different:$server"))),
        )
    }

    @Test
    fun foreignScopeIdentityCreatorAndBindingNeverBecomeOwnedRoomMetadata() {
        for (invalid in
            listOf(
                bound.copy(intent = intent.copy(spaceId = UUID.randomUUID())),
                bound.copy(intent = intent.copy(firstMatrixUserId = "@foreign:$server")),
                bound.copy(intent = intent.copy(requestedBy = UUID.randomUUID())),
                bound.copy(intent = intent.copy(matrixCreatorAccountId = pair[1])),
                bound.copy(binding = binding.copy(conversationId = UUID.randomUUID())),
                bound.copy(binding = binding.copy(matrixCreatorAccountId = pair[1])),
                bound.copy(binding = binding.copy(publishedBy = UUID.randomUUID())),
                bound.copy(binding = binding.copy(leaseToken = UUID(0, 0))),
                bound.copy(binding = binding.copy(requestRevision = -1)),
            )) assertInstanceOf(Result.Failed::class.java, snapshot(invalid))
        assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationSpaceBoundRoom(space, UUID.randomUUID(), server, room, bound, at),
        )
        assertEquals("collaboration_room_not_found", (snapshot(null) as Result.Failed).failure.code)
    }

    @Test
    fun futureNoncanonicalOrReversedMetadataTimesFailClosed() {
        for (time in
            listOf(
                at.plusSeconds(1),
                at.minusSeconds(61),
                at.minusSeconds(1).plusNanos(1),
                Instant.MAX,
            )) assertInstanceOf(
            Result.Failed::class.java,
            snapshot(bound.copy(binding = binding.copy(createdAt = time))),
        )
    }
}
