package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceReadScopePolicyTest {
    private val space = UUID.randomUUID()
    private val company = UUID.randomUUID()
    private val account = UUID(0, 1)
    private val peer = UUID(0, 2)
    private val device = UUID.randomUUID()
    private val oldDevice = UUID.randomUUID()
    private val server = "matrix.example.test"
    private val at = Instant.parse("2026-11-04T12:00:00Z")

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun user(id: UUID, namespace: UUID = company) =
        "@${collaborationMatrixLocalpart(namespace, id)}:$server"

    private val owner =
        CollaborationSpaceHistoryOwner(
            space,
            account,
            device,
            server,
            user(account),
            "HRISG_${compact(device)}",
        )
    private val retained =
        owner.copy(
            matrixDeviceId = "HRIS_${compact(oldDevice)}",
            legacy = CollaborationLegacyDevicePosition(company, oldDevice),
        )

    private fun original(peer: UUID = this.peer, id: UUID = UUID.randomUUID()) =
        CollaborationSpaceLegacyConversation(
            space,
            CollaborationDirectConversation(
                id,
                company,
                account,
                peer,
                account,
                at.minusSeconds(120),
            ),
            CollaborationLegacyRoomMetadata(
                server,
                "!original-$id:$server",
                at.minusSeconds(60),
                peer,
            ),
        )

    private fun published(
        original: CollaborationSpaceLegacyConversation? = null
    ): CollaborationSpaceBoundRoom {
        val conversation = UUID.randomUUID()
        val oldRoom = original?.room
        val creator = oldRoom?.createdBy ?: account
        val roomId = oldRoom?.roomId ?: "!new-$conversation:$server"
        return CollaborationSpaceBoundRoom(
            CollaborationSpaceRoomIntent(
                space,
                conversation,
                account,
                peer,
                account,
                creator,
                server,
                user(account),
                if (original == null) user(peer, UUID.randomUUID()) else user(peer),
                if (original == null) CollaborationSpaceRoomOrigin.SPACE
                else CollaborationSpaceRoomOrigin.LEGACY,
                original?.let {
                    CollaborationSpaceOriginalRoom(
                        company,
                        it.conversation.id,
                        roomId,
                        requireNotNull(it.room).boundAt,
                    )
                },
                at.minusSeconds(30),
            ),
            CollaborationSpaceRoomBinding(
                space,
                conversation,
                account,
                0,
                UUID.randomUUID(),
                roomId,
                at.minusSeconds(20),
                creator,
            ),
        )
    }

    private fun read(
        groups: List<CollaborationSpaceBoundRoom>,
        old: List<CollaborationSpaceLegacyConversation>,
        owner: CollaborationSpaceHistoryOwner = this.owner,
    ) = snapshotCollaborationSpaceReadScope(owner, groups, old, at.plusNanos(123))

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }

    @Test
    fun directHintsRetainOriginalPeerAliasesThroughAdoptionAndKeepNewAliasesDistinct() {
        val old = original()
        val oldRoomId = requireNotNull(old.room).roomId
        val adopted = published(old)
        val separate =
            published().let {
                it.copy(
                    intent =
                        it.intent.copy(
                            secondMatrixUserId =
                                "@${collaborationSpaceMatrixLocalpart(space, peer)}:$server"
                        )
                )
            }
        for (current in listOf(owner, retained)) {
            val originalHints =
                collaborationSpaceDirectMetadata(
                    current,
                    success(read(emptyList(), listOf(old), current)),
                )
            assertEquals(mapOf(user(peer) to listOf(oldRoomId)), originalHints)
            assertEquals(
                originalHints,
                collaborationSpaceDirectMetadata(
                    current,
                    success(read(listOf(adopted), listOf(old), current)),
                ),
            )
            val combined =
                collaborationSpaceDirectMetadata(
                    current,
                    success(read(listOf(separate), listOf(old), current)),
                )
            assertEquals(setOf(user(peer), separate.intent.secondMatrixUserId), combined.keys)
            assertEquals(listOf(oldRoomId), combined[user(peer)])
            assertEquals(
                listOf(separate.binding.roomId),
                combined[separate.intent.secondMatrixUserId],
            )
            assertEquals(Result.Success(combined), normalizeCollaborationDirectMetadata(combined))
            assertTrue(
                collaborationSpaceDirectMetadata(
                        current,
                        success(read(emptyList(), emptyList(), current)),
                    )
                    .isEmpty()
            )
        }
    }

    @Test
    fun emptyScopesStillRequireCanonicalFreshOrRetainedDeviceCoordinates() {
        val freshGroup =
            owner.copy(
                matrixUserId = "@${collaborationSpaceMatrixLocalpart(space, account)}:$server"
            )
        for (valid in listOf(owner, retained, freshGroup)) {
            assertTrue(validCollaborationSpaceHistoryIdentity(valid))
            assertTrue(success(read(emptyList(), emptyList(), valid)).rooms.isEmpty())
        }
        assertEquals(company, collaborationSpaceHistoryOriginalCompany(owner))
        assertEquals(company, collaborationSpaceHistoryOriginalCompany(retained))
        assertNull(collaborationSpaceHistoryOriginalCompany(freshGroup))
        for (invalid in
            listOf(
                owner.copy(spaceId = UUID(0, 0)),
                owner.copy(accountId = UUID(0, 0)),
                owner.copy(deviceId = UUID(0, 0)),
                owner.copy(matrixDeviceId = retained.matrixDeviceId),
                owner.copy(matrixUserId = user(peer)),
                owner.copy(matrixUserId = user(account, UUID(0, 0))),
                owner.copy(serverName = "other.example.test"),
                owner.copy(legacy = retained.legacy),
                retained.copy(
                    legacy = CollaborationLegacyDevicePosition(UUID.randomUUID(), oldDevice)
                ),
                retained.copy(matrixDeviceId = owner.matrixDeviceId),
            )) {
            assertFalse(validCollaborationSpaceHistoryIdentity(invalid))
            assertNull(collaborationSpaceHistoryOriginalCompany(invalid))
            assertInstanceOf(Result.Failed::class.java, read(emptyList(), emptyList(), invalid))
        }
        assertInstanceOf(
            Result.Failed::class.java,
            read(emptyList(), listOf(original()), freshGroup),
        )
    }

    @Test
    fun originalOwnershipKeepsActualCreatorAndRejectsForeignOrFutureMetadata() {
        val old = original()
        val originalRoom = requireNotNull(old.room)
        for (valid in listOf(owner, retained)) assertEquals(
            Result.Success(old),
            snapshotCollaborationSpaceOriginalReadRoom(valid, old, at),
        )
        for (invalid in
            listOf(
                old.copy(spaceId = UUID.randomUUID()),
                old.copy(conversation = old.conversation.copy(companyId = UUID.randomUUID())),
                old.copy(conversation = old.conversation.copy(id = UUID(0, 0))),
                old.copy(conversation = old.conversation.copy(firstAccountId = peer)),
                old.copy(conversation = old.conversation.copy(version = 1)),
                old.copy(conversation = old.conversation.copy(createdAt = at.minusSeconds(1))),
                old.copy(room = null),
                old.copy(room = originalRoom.copy(createdBy = UUID.randomUUID())),
                old.copy(room = originalRoom.copy(serverName = "other.example.test")),
                old.copy(room = originalRoom.copy(boundAt = at.plusSeconds(1))),
                old.copy(room = originalRoom.copy(boundAt = at.minusNanos(1))),
            )) assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationSpaceOriginalReadRoom(owner, invalid, at),
        )
    }

    @Test
    fun originalAndCurrentPeerAliasesRemainDistinctWithoutInventingGroupBindings() {
        val old = original()
        val group = published()
        assertNotEquals(user(peer), group.intent.secondMatrixUserId)
        val result = success(read(listOf(group), listOf(old)))
        assertEquals(2, result.rooms.size)
        val published =
            result.rooms.filterIsInstance<CollaborationSpaceReadRoom.Published>().single()
        assertEquals(group, published.value)
        assertNull(published.original)
        assertEquals(
            old,
            result.rooms.filterIsInstance<CollaborationSpaceReadRoom.Original>().single().value,
        )
        assertEquals(result.rooms.map { it.roomId }.sorted(), result.rooms.map { it.roomId })
    }

    @Test
    fun exactAdoptionFoldsOnceAndKeepsBothOriginalAndPublishedMetadata() {
        val old = original()
        val originalRoom = requireNotNull(old.room)
        val group = published(old)
        val room =
            success(read(listOf(group), listOf(old))).rooms.single()
                as CollaborationSpaceReadRoom.Published
        assertEquals(group, room.value)
        assertEquals(old, room.original)
        assertInstanceOf(Result.Failed::class.java, read(listOf(group), emptyList()))
        for (changed in
            listOf(
                old.copy(conversation = old.conversation.copy(id = UUID.randomUUID())),
                old.copy(room = originalRoom.copy(createdBy = account)),
                old.copy(room = originalRoom.copy(boundAt = originalRoom.boundAt.minusSeconds(1))),
                old.copy(room = originalRoom.copy(roomId = "!different:$server")),
            )) assertInstanceOf(Result.Failed::class.java, read(listOf(group), listOf(changed)))
        val spaceOverlap =
            group.copy(
                intent =
                    group.intent.copy(
                        origin = CollaborationSpaceRoomOrigin.SPACE,
                        original = null,
                        matrixCreatorAccountId = account,
                    ),
                binding = group.binding.copy(matrixCreatorAccountId = account),
            )
        assertInstanceOf(Result.Failed::class.java, read(listOf(spaceOverlap), listOf(old)))
    }

    @Test
    fun snapshotsDetachInputAndPinAllOriginalMetadataInTheDigest() {
        val a = original()
        val b = original(UUID(0, 3))
        val group = published()
        val input = mutableListOf(a, b)
        val result = success(read(listOf(group), input))
        assertEquals(result, success(read(listOf(group), input.reversed())))
        input.clear()
        assertEquals(3, result.rooms.size)
        assertNotEquals(
            result.digest,
            success(
                    read(
                        listOf(group),
                        listOf(
                            a,
                            b.copy(conversation = b.conversation.copy(createdBy = UUID(0, 3))),
                        ),
                    )
                )
                .digest,
        )
        assertNotEquals(result.digest, success(read(listOf(group), listOf(a, b), retained)).digest)
        assertInstanceOf(Result.Failed::class.java, read(emptyList(), listOf(a, a)))
        assertInstanceOf(
            Result.Failed::class.java,
            read(emptyList(), listOf(a, b.copy(room = b.room!!.copy(roomId = a.room!!.roomId)))),
        )
    }

    @Test
    fun unionCapacityCountsUniqueRoomsAndNeverTruncatesAnIncompleteSource() {
        val originals =
            (2..COLLABORATION_SYNC_ROOM_LIMIT + 1).map { original(UUID(0, it.toLong())) }
        assertEquals(
            COLLABORATION_SYNC_ROOM_LIMIT,
            success(read(emptyList(), originals)).rooms.size,
        )
        val adopted = published(originals.first())
        assertEquals(
            COLLABORATION_SYNC_ROOM_LIMIT,
            success(read(listOf(adopted), originals)).rooms.size,
        )
        assertEquals(
            Failure(FailureKind.UNAVAILABLE, "collaboration_sync_scope_capacity"),
            (read(listOf(published()), originals) as Result.Failed).failure,
        )
        assertEquals(
            Failure(FailureKind.UNAVAILABLE, "collaboration_sync_scope_capacity"),
            (read(emptyList(), originals + original()) as Result.Failed).failure,
        )
    }
}
