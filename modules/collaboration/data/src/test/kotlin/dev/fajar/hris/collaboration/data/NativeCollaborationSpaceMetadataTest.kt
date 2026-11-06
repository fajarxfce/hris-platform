package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationSpaceRoomDataSource
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import java.time.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceMetadataTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun directHintsAreManagedAndOwnedHistorySurvivesPeerOffboarding() {
        val f = ready()
        val metadata = Metadata(f)
        val command = f.command(f.provider(), roomAccess = metadata)
        val original = f.provenance()
        val hints =
            success(command.directMetadata(f.tokens.accessToken, UUID.randomUUID(), f.s.user))
        assertEquals(mapOf(f.user(f.peer) to listOf(f.boundRoom)), hints)
        success(
            command.confirmDirectMetadata(f.tokens.accessToken, UUID.randomUUID(), f.s.user, hints)
        )
        assertEquals(
            "collaboration_direct_metadata_managed",
            failed(
                command.confirmDirectMetadata(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.s.user,
                    emptyMap(),
                )
            ),
        )
        assertEquals(
            "collaboration_direct_metadata_unavailable",
            failed(command.directMetadata(f.tokens.accessToken, UUID.randomUUID(), f.user(f.peer))),
        )
        assertEquals(
            "invalid_collaboration_direct_metadata",
            failed(
                command.confirmDirectMetadata(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.s.user,
                    mapOf(f.user(f.peer) to listOf(f.boundRoom, f.boundRoom)),
                )
            ),
        )
        f.revokePeer()
        f.s.owner.update("delete from native_sessions where id=?", f.peerParent)
        assertEquals(
            hints,
            success(command.directMetadata(f.tokens.accessToken, UUID.randomUUID(), f.s.user)),
        )
        success(command.roomState(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom))
        assertEquals(original, f.provenance())
        assertEquals(listOf("state"), metadata.calls)
        assertEquals(0, f.leases())
    }

    @Test
    fun metadataUsesExactOwnedFrameAndDecryptsOnlyItsAuthenticatedMemberPositionOutsideSql() {
        val f = ready()
        val metadata = Metadata(f)
        val cursors = EncryptedCollaborationSpaceSyncCursorRepository(collaborationCursorFixture())
        val command = f.command(f.provider(), roomAccess = metadata, syncCursors = cursors)
        success(command.roomState(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom))
        assertEquals(
            f.boundRoom,
            success(command.joinRoom(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom)),
        )
        val at =
            success(
                cursors.seal(
                    owner(f),
                    CollaborationSyncPosition("a".repeat(64), "original-member-position", "42"),
                )
            )
        success(command.roomMembers(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, at))
        assertEquals("original-member-position", metadata.position)
        success(command.roomMembers(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, null))
        assertNull(metadata.position)
        val before = metadata.calls.size
        assertEquals(
            "invalid_collaboration_sync_cursor",
            failed(
                command.roomMembers(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.boundRoom,
                    "raw-provider-position",
                )
            ),
        )
        assertEquals(
            "invalid_collaboration_room_request",
            failed(
                command.roomMembers(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.boundRoom,
                    "bad\nwire",
                )
            ),
        )
        assertEquals(
            "collaboration_room_not_found",
            failed(
                command.roomState(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    "!foreign:matrix.example.test",
                )
            ),
        )
        assertEquals(
            "invalid_collaboration_room_request",
            failed(command.joinRoom(f.tokens.accessToken, UUID.randomUUID(), "invalid")),
        )
        assertEquals(before, metadata.calls.size)
        assertEquals(0, f.leases())
    }

    @Test
    fun receiptsRequireMappedOriginalEventBeforeAnyProviderMetadataWrite() {
        val f = ready()
        val metadata = Metadata(f)
        val history = History(f)
        val command = f.command(f.provider(), roomAccess = metadata, roomHistory = history)
        history.failure = Failure(FailureKind.NOT_FOUND, "collaboration_event_not_found")
        assertEquals(
            "collaboration_event_not_found",
            failed(
                command.sendReadReceipt(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.boundRoom,
                    "m.read.private",
                    "\$original",
                )
            ),
        )
        assertTrue(metadata.calls.isEmpty())
        history.failure = null
        for (type in listOf("m.read.private", "m.read")) {
            success(
                command.sendReadReceipt(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.boundRoom,
                    type,
                    "\$original",
                )
            )
            assertEquals(type, metadata.receiptType)
            assertEquals("\$original", metadata.eventId)
        }
        assertEquals(3, history.calls)
        assertEquals(listOf("receipt", "receipt"), metadata.calls)
        for ((type, event) in listOf("m.fully_read" to "\$original", "m.read" to "invalid")) {
            assertEquals(
                "invalid_collaboration_read_receipt",
                failed(
                    command.sendReadReceipt(
                        f.tokens.accessToken,
                        UUID.randomUUID(),
                        f.boundRoom,
                        type,
                        event,
                    )
                ),
            )
        }
        assertEquals(3, history.calls)
        assertEquals(0, f.leases())
    }

    @Test
    fun nativeRevocationAfterEventProofPreventsReceiptWrite() {
        val f = ready()
        val metadata = Metadata(f)
        val history = History(f)
        history.after = { revoke(f) }
        val command = f.command(f.provider(), roomAccess = metadata, roomHistory = history)
        val result =
            command.sendReadReceipt(
                f.tokens.accessToken,
                UUID.randomUUID(),
                f.boundRoom,
                "m.read.private",
                "\$original",
            ) as Result.Failed
        assertEquals(FailureKind.UNAUTHENTICATED, result.failure.kind)
        assertEquals(1, history.calls)
        assertTrue(metadata.calls.isEmpty())
        assertEquals(0, f.leases())
    }

    @Test
    fun nativeRevocationAfterMetadataWriteWithholdsAcknowledgement() {
        val f = ready()
        val metadata = Metadata(f)
        val history = History(f)
        metadata.after = { revoke(f) }
        val command = f.command(f.provider(), roomAccess = metadata, roomHistory = history)
        val result =
            command.sendReadReceipt(
                f.tokens.accessToken,
                UUID.randomUUID(),
                f.boundRoom,
                "m.read.private",
                "\$original",
            ) as Result.Failed
        assertEquals(FailureKind.UNAUTHENTICATED, result.failure.kind)
        assertEquals(1, history.calls)
        assertEquals(listOf("receipt"), metadata.calls)
        assertEquals(0, f.leases())
    }

    @Test
    fun immutableRoomChangeWithholdsEitherMetadataProviderOutcome() {
        val f = ready()
        val actual =
            StoredCollaborationSpaceRoomRepository(
                PostgresCollaborationSpaceRoomDataSource(f.base.sql)
            )
        val changedId = UUID.randomUUID()
        var changed = false
        val rows =
            object : CollaborationSpaceRoomRepository by actual {
                override fun boundRoom(
                    spaceId: UUID,
                    accountId: UUID,
                    serverName: String,
                    roomId: String,
                ): Result<CollaborationSpaceBoundRoom?> =
                    actual.boundRoom(spaceId, accountId, serverName, roomId).map { row ->
                        if (row != null && changed)
                            row.copy(
                                intent = row.intent.copy(conversationId = changedId),
                                binding = row.binding.copy(conversationId = changedId),
                            )
                        else row
                    }
            }
        val metadata = Metadata(f)
        val command = f.command(f.provider(), roomAccess = metadata, rooms = rows)
        metadata.after = { changed = true }
        for (fails in listOf(false, true)) {
            changed = false
            metadata.failure =
                if (fails) Failure(FailureKind.UNAVAILABLE, "fixture_metadata_failed") else null
            assertEquals(
                "collaboration_room_changed",
                failed(command.roomState(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom)),
            )
            assertEquals(0, f.leases())
        }
        assertEquals(2, metadata.calls.size)
    }

    @Test
    fun directInventoryChangeAndAcquisitionFailureNeverReleaseManagedHints() {
        val f = ready()
        val actual =
            StoredCollaborationSpaceRoomRepository(
                PostgresCollaborationSpaceRoomDataSource(f.base.sql)
            )
        val changedId = UUID.randomUUID()
        var reads = 0
        var fail = false
        val rows =
            object : CollaborationSpaceRoomRepository by actual {
                override fun ownedBoundRooms(
                    spaceId: UUID,
                    accountId: UUID,
                    serverName: String,
                    limit: Int,
                ): Result<List<CollaborationSpaceBoundRoom>> {
                    if (fail)
                        return Result.Failed(
                            Failure(FailureKind.UNAVAILABLE, "fixture_inventory_failed")
                        )
                    reads++
                    return actual.ownedBoundRooms(spaceId, accountId, serverName, limit).map {
                        values ->
                        if (reads == 1) values
                        else
                            values.map {
                                it.copy(
                                    intent = it.intent.copy(conversationId = changedId),
                                    binding = it.binding.copy(conversationId = changedId),
                                )
                            }
                    }
                }
            }
        val command = f.command(f.provider(), rooms = rows)
        assertEquals(
            "collaboration_direct_metadata_changed",
            failed(command.directMetadata(f.tokens.accessToken, UUID.randomUUID(), f.s.user)),
        )
        fail = true
        assertEquals(
            "fixture_inventory_failed",
            failed(command.directMetadata(f.tokens.accessToken, UUID.randomUUID(), f.s.user)),
        )
        assertEquals(0, f.leases())
    }

    @Test
    fun metadataCancellationAndLateResultsRetainTheOriginalDeadlineAndReleaseOwnership() {
        val f = ready()
        val metadata = Metadata(f)
        var offset = 0L
        val clock =
            object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC

                override fun withZone(zone: ZoneId): Clock = this

                override fun instant(): Instant = Instant.now().plusSeconds(offset)
            }
        val command = f.command(f.provider(), roomAccess = metadata, clock = clock)
        metadata.after = { throw CancellationException("fictional metadata cancellation") }
        assertThrows(CancellationException::class.java) {
            command.joinRoom(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom)
        }
        assertEquals(0, f.leases())
        metadata.after = { throw InterruptedException("fictional metadata interruption") }
        assertThrows(InterruptedException::class.java) {
            command.roomMembers(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, null)
        }
        assertEquals(0, f.leases())
        metadata.after = { offset = 31 }
        assertEquals(
            "collaboration_admission_changed",
            failed(command.roomState(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom)),
        )
        assertEquals(0, f.leases())
    }

    private fun ready() = SpaceKeyExchangeFixture(postgres).also { it.bind() }

    private fun owner(f: SpaceKeyExchangeFixture) =
        CollaborationSpaceHistoryOwner(
            f.s.space,
            f.s.account,
            f.s.device,
            "matrix.example.test",
            f.s.user,
            f.ownKeys.deviceId,
        )

    private fun revoke(f: SpaceKeyExchangeFixture) {
        f.s.owner.update(
            "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
            f.s.parent,
        )
    }

    private fun checkFrame(
        f: SpaceKeyExchangeFixture,
        current: CollaborationSpaceHistoryOwner,
        roomId: String,
        bearer: String,
        deadline: Instant,
    ) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
        assertEquals(owner(f), current)
        assertEquals(f.boundRoom, roomId)
        assertEquals("fixture-owned-query-token", bearer)
        assertTrue(deadline > Instant.now() && deadline <= Instant.now().plusSeconds(30))
        assertTrue(f.leases() in 1..7)
    }

    private inner class History(private val f: SpaceKeyExchangeFixture) :
        CollaborationSpaceMatrixRoomHistoryRepository by DisabledCollaborationSpaceMatrixRoomHistoryRepository() {
        var calls = 0
        var after: () -> Unit = {}
        var failure: Failure? = null

        override fun event(
            owner: CollaborationSpaceHistoryOwner,
            room: CollaborationSpaceBoundRoom,
            eventId: String,
            bearer: String,
            deadline: Instant,
        ): Result<CollaborationRoomHistory> {
            checkFrame(f, owner, room.binding.roomId, bearer, deadline)
            assertEquals("\$original", eventId)
            calls++
            after()
            return failure?.let { Result.Failed(it) }
                ?: Result.Success(CollaborationRoomHistory(mapOf("event_id" to eventId)))
        }
    }

    private inner class Metadata(private val f: SpaceKeyExchangeFixture) :
        CollaborationSpaceMatrixRoomAccessRepository {
        val calls = mutableListOf<String>()
        var after: () -> Unit = {}
        var failure: Failure? = null
        var position: String? = null
        var receiptType: String? = null
        var eventId: String? = null

        private fun read(
            kind: String,
            owner: CollaborationSpaceHistoryOwner,
            room: CollaborationSpaceReadRoom,
            bearer: String,
            deadline: Instant,
        ): Result<Unit> {
            assertInstanceOf(CollaborationSpaceReadRoom.Published::class.java, room)
            checkFrame(f, owner, room.roomId, bearer, deadline)
            calls += kind
            after()
            return failure?.let { Result.Failed(it) } ?: Result.Success(Unit)
        }

        override fun state(
            owner: CollaborationSpaceHistoryOwner,
            room: CollaborationSpaceReadRoom,
            bearer: String,
            deadline: Instant,
        ): Result<CollaborationRoomEvents> =
            read("state", owner, room, bearer, deadline).map {
                CollaborationRoomEvents(emptyList())
            }

        override fun members(
            owner: CollaborationSpaceHistoryOwner,
            room: CollaborationSpaceReadRoom,
            at: String?,
            bearer: String,
            deadline: Instant,
        ): Result<CollaborationRoomEvents> {
            position = at
            return read("members", owner, room, bearer, deadline).map {
                CollaborationRoomEvents(emptyList())
            }
        }

        override fun join(
            owner: CollaborationSpaceHistoryOwner,
            room: CollaborationSpaceReadRoom,
            bearer: String,
            deadline: Instant,
        ): Result<String> = read("join", owner, room, bearer, deadline).map { room.roomId }

        override fun receipt(
            owner: CollaborationSpaceHistoryOwner,
            room: CollaborationSpaceReadRoom,
            receiptType: String,
            eventId: String,
            bearer: String,
            deadline: Instant,
        ): Result<Unit> {
            this.receiptType = receiptType
            this.eventId = eventId
            return read("receipt", owner, room, bearer, deadline)
        }
    }

    private fun failed(result: Result<*>) = (result as Result.Failed).failure.code

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            result,
            (result as? Result.Failed)?.failure?.code,
        )
        return (result as Result.Success).value
    }
}
