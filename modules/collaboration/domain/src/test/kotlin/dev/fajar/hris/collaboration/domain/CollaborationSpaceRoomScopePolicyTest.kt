package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceRoomScopePolicyTest {
    private val space = UUID.randomUUID()
    private val account = UUID(0, 1)
    private val server = "matrix.example.test"
    private val at = Instant.parse("2026-11-04T12:00:00Z")

    private fun user(id: UUID, company: UUID? = null) =
        "@${if (company == null) collaborationSpaceMatrixLocalpart(space, id) else collaborationMatrixLocalpart(company, id)}:$server"

    private fun room(
        peer: UUID,
        company: UUID? = null,
        original: Boolean = false,
    ): CollaborationSpaceBoundRoom {
        val conversation = UUID.randomUUID()
        val roomId = "!$conversation:$server"
        val creator = if (original) peer else account
        return CollaborationSpaceBoundRoom(
            CollaborationSpaceRoomIntent(
                space,
                conversation,
                account,
                peer,
                account,
                creator,
                server,
                user(account, company),
                user(peer, if (original) company else null),
                if (original) CollaborationSpaceRoomOrigin.LEGACY
                else CollaborationSpaceRoomOrigin.SPACE,
                if (original)
                    CollaborationSpaceOriginalRoom(
                        requireNotNull(company),
                        UUID.randomUUID(),
                        roomId,
                        at.minusSeconds(120),
                    )
                else null,
                at.minusSeconds(60),
            ),
            CollaborationSpaceRoomBinding(
                space,
                conversation,
                account,
                0,
                UUID.randomUUID(),
                roomId,
                at.minusSeconds(30),
                creator,
            ),
        )
    }

    private fun snapshot(rooms: List<CollaborationSpaceBoundRoom>) =
        snapshotCollaborationSpaceRoomScope(space, account, server, rooms, at)

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    @Test
    fun orderingDoesNotChangeTheDigestAndAcquiredListIsDetached() {
        val a = room(UUID(0, 2))
        val b = room(UUID(0, 3))
        val input = mutableListOf(a, b)
        val first = success(snapshot(input))
        assertEquals(first, success(snapshot(input.reversed())))
        assertEquals(listOf(a, b).sortedBy { it.intent.conversationId.toString() }, first.rooms)
        input.clear()
        assertEquals(2, first.rooms.size)
        val empty = success(snapshot(emptyList()))
        assertNotEquals(first.digest, empty.digest)
        assertEquals(empty, success(snapshot(emptyList())))
        assertNotEquals(
            empty.digest,
            success(snapshotCollaborationSpaceRoomScope(space, UUID(0, 5), server, emptyList(), at))
                .digest,
        )
    }

    @Test
    fun retainedAndNewRoomsKeepOriginalUsersAndActualCreators() {
        val company = UUID.randomUUID()
        val legacy = room(UUID(0, 2), company, original = true)
        val mixed = room(UUID(0, 3), company)
        val scope = success(snapshot(listOf(mixed, legacy)))
        assertEquals(setOf(legacy, mixed), scope.rooms.toSet())
        assertEquals(user(account, company), legacy.intent.firstMatrixUserId)
        assertEquals(UUID(0, 2), legacy.binding.matrixCreatorAccountId)
        val changed = mixed.copy(binding = mixed.binding.copy(roomId = "!another:$server"))
        assertNotEquals(scope.digest, success(snapshot(listOf(legacy, changed))).digest)
        val creatorChanged =
            legacy.copy(
                intent = legacy.intent.copy(matrixCreatorAccountId = account),
                binding = legacy.binding.copy(matrixCreatorAccountId = account),
            )
        assertNotEquals(scope.digest, success(snapshot(listOf(mixed, creatorChanged))).digest)
    }

    @Test
    fun foreignInvalidDuplicateAndFutureBoundRoomsFailClosed() {
        val a = room(UUID(0, 2))
        val b = room(UUID(0, 3))
        for (input in
            listOf(
                listOf(a, a),
                listOf(a, b.copy(binding = b.binding.copy(roomId = a.binding.roomId))),
                listOf(a.copy(intent = a.intent.copy(spaceId = UUID.randomUUID()))),
                listOf(a.copy(intent = a.intent.copy(matrixCreatorAccountId = UUID(0, 99)))),
                listOf(a.copy(binding = a.binding.copy(createdAt = at.plusSeconds(1)))),
                listOf(a.copy(binding = a.binding.copy(createdAt = at.minusNanos(1)))),
            )) assertEquals(
            "collaboration_room_scope_invalid",
            (snapshot(input) as Result.Failed).failure.code,
        )
        for ((ownerSpace, ownerAccount, ownerServer) in
            listOf(
                Triple(UUID(0, 0), account, server),
                Triple(space, UUID(0, 0), server),
                Triple(space, account, "invalid server"),
            )) assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationSpaceRoomScope(
                ownerSpace,
                ownerAccount,
                ownerServer,
                emptyList(),
                at,
            ),
        )
    }

    @Test
    fun oneAccountCannotChangeItsCanonicalProviderUserAcrossRooms() {
        val a = room(UUID(0, 2))
        val b = room(UUID(0, 3), UUID.randomUUID())
        assertInstanceOf(Result.Success::class.java, snapshot(listOf(a)))
        assertInstanceOf(Result.Success::class.java, snapshot(listOf(b)))
        assertEquals(
            "collaboration_room_scope_invalid",
            (snapshot(listOf(a, b)) as Result.Failed).failure.code,
        )
    }

    @Test
    fun capacityIsExplicitAndNeverReturnsATruncatedScope() {
        val rooms = (2..COLLABORATION_SYNC_ROOM_LIMIT + 1).map { room(UUID(0, it.toLong())) }
        assertEquals(COLLABORATION_SYNC_ROOM_LIMIT, success(snapshot(rooms)).rooms.size)
        assertEquals(
            Failure(FailureKind.UNAVAILABLE, "collaboration_sync_scope_capacity"),
            (snapshot(rooms + room(UUID(0, COLLABORATION_SYNC_ROOM_LIMIT + 2L))) as Result.Failed)
                .failure,
        )
    }
}
