package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationSpaceRoomDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceRoomRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceRoomHistoryTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun allThreeReadsPreserveOwnedRoomAndOriginalDeviceWithoutRequiringNewSendKeys() {
        val f = ready()
        val history = History(f)
        val keys = f.provider()
        val command = f.command(keys, roomHistory = history)
        val before = f.provenance()
        val page =
            CollaborationRoomPageRequest(
                "original-sdk-cursor",
                null,
                "b",
                7,
                CollaborationRoomHistoryFilter(true, true),
            )
        success(command.roomMessages(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, page))
        success(
            command.roomContext(
                f.tokens.accessToken,
                UUID.randomUUID(),
                f.boundRoom,
                "\$original",
                0,
            )
        )
        success(
            command.roomEvent(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, "\$original")
        )
        assertEquals(listOf("messages", "context", "event"), history.calls)
        assertEquals(page, history.page)
        assertEquals("\$original", history.eventId)
        assertEquals(before, f.provenance())
        assertEquals(0, keys.ownReads.get())
        assertEquals(0, f.leases())
    }

    @Test
    fun unknownUnboundForeignAndMalformedReadsNeverReachProviderHistory() {
        val f = SpaceKeyExchangeFixture(postgres)
        val history = History(f)
        val command = f.command(f.provider(), roomHistory = history)
        fun page(
            room: String = f.boundRoom,
            request: CollaborationRoomPageRequest =
                CollaborationRoomPageRequest(null, null, "b", 10),
        ) = command.roomMessages(f.tokens.accessToken, UUID.randomUUID(), room, request)
        assertEquals("collaboration_room_not_found", failed(page()))
        f.bind()
        assertEquals("collaboration_room_not_found", failed(page("!foreign:matrix.example.test")))
        assertEquals("invalid_collaboration_room_request", failed(page("invalid")))
        for (request in
            listOf(
                CollaborationRoomPageRequest(null, null, "x", 10),
                CollaborationRoomPageRequest(null, null, "b", 0),
                CollaborationRoomPageRequest(null, null, "f", 101),
                CollaborationRoomPageRequest("x".repeat(4097), null, "b", 10),
                CollaborationRoomPageRequest(null, "private\nposition", "b", 10),
                CollaborationRoomPageRequest(
                    null,
                    null,
                    "b",
                    10,
                    CollaborationRoomHistoryFilter(false, true),
                ),
            )) assertEquals(
            "invalid_collaboration_history_request",
            failed(page(request = request)),
        )
        assertEquals(
            "invalid_collaboration_history_request",
            failed(
                command.roomContext(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.boundRoom,
                    "\$original",
                    101,
                )
            ),
        )
        assertEquals(
            "invalid_collaboration_history_request",
            failed(
                command.roomEvent(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, "invalid")
            ),
        )
        assertTrue(history.calls.isEmpty())
        assertEquals(0, f.leases())
    }

    @Test
    fun peerOffboardingBeforeOrDuringReadRetainsHistoryButDeniesANewSend() {
        val f = ready()
        f.reserve()
        f.reserve(f.peer)
        val history = History(f)
        val command = f.command(f.provider(), roomHistory = history)
        history.after = { f.revokePeer() }
        success(
            command.roomEvent(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, "\$original")
        )
        history.after = {}
        success(
            command.roomMessages(
                f.tokens.accessToken,
                UUID.randomUUID(),
                f.boundRoom,
                CollaborationRoomPageRequest(null, null, "b", 10),
            )
        )
        assertEquals(
            "collaboration_recipient_unavailable",
            failed(
                command.sendRoomMessage(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.boundRoom,
                    CollaborationRoomMessage(
                        "original-send",
                        "ZmljdGlvbmFs",
                        java.util.Base64.getEncoder()
                            .withoutPadding()
                            .encodeToString(ByteArray(32) { 7 }),
                    ),
                )
            ),
        )
        assertEquals(2, history.calls.size)
        assertEquals(0, f.leases())
    }

    @Test fun nativeRevocationAfterSuccessfulReadWithholdsTheResponse() = revokeAfterRead(false)

    @Test
    fun nativeRevocationAfterFailedReadTakesPrecedenceOverProviderFailure() = revokeAfterRead(true)

    private fun revokeAfterRead(fails: Boolean) {
        val f = ready()
        val history = History(f)
        if (fails) history.failure = Failure(FailureKind.UNAVAILABLE, "fixture_history_failed")
        history.after = {
            f.s.owner.update(
                "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
                f.s.parent,
            )
        }
        val command = f.command(f.provider(), roomHistory = history)
        val result =
            command.roomEvent(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, "\$original")
        assertEquals(FailureKind.UNAUTHENTICATED, (result as Result.Failed).failure.kind)
        assertEquals(1, history.calls.size)
        assertEquals(0, f.leases())
        assertEquals(
            FailureKind.UNAUTHENTICATED,
            (command.roomContext(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.boundRoom,
                    "\$original",
                    0,
                ) as Result.Failed)
                .failure
                .kind,
        )
        assertEquals(1, history.calls.size)
    }

    @Test
    fun changedOwnedMetadataWithholdsEitherProviderOutcome() {
        val f = ready()
        val real =
            StoredCollaborationSpaceRoomRepository(
                PostgresCollaborationSpaceRoomDataSource(f.base.sql)
            )
        var changed = false
        val replacement = UUID.randomUUID()
        val rooms =
            object : CollaborationSpaceRoomRepository by real {
                override fun boundRoom(
                    spaceId: UUID,
                    accountId: UUID,
                    serverName: String,
                    roomId: String,
                ): Result<CollaborationSpaceBoundRoom?> =
                    real.boundRoom(spaceId, accountId, serverName, roomId).map { original ->
                        if (!changed || original == null) original
                        else
                            original.copy(
                                intent = original.intent.copy(conversationId = replacement),
                                binding = original.binding.copy(conversationId = replacement),
                            )
                    }
            }
        val history = History(f)
        history.after = { changed = true }
        val command = f.command(f.provider(), rooms = rooms, roomHistory = history)
        for (fails in listOf(false, true)) {
            changed = false
            history.failure =
                if (fails) Failure(FailureKind.UNAVAILABLE, "fixture_history_failed") else null
            assertEquals(
                "collaboration_room_changed",
                failed(
                    command.roomEvent(
                        f.tokens.accessToken,
                        UUID.randomUUID(),
                        f.boundRoom,
                        "\$original",
                    )
                ),
            )
            assertEquals(0, f.leases())
        }
        assertEquals(2, history.calls.size)
    }

    @Test
    fun cancellationAndInterruptionPropagateAndReleaseOnlyTheOwnedRequest() {
        val f = ready()
        val history = History(f)
        val command = f.command(f.provider(), roomHistory = history)
        history.after = { throw CancellationException("fictional cancelled history") }
        assertThrows(CancellationException::class.java) {
            command.roomMessages(
                f.tokens.accessToken,
                UUID.randomUUID(),
                f.boundRoom,
                CollaborationRoomPageRequest(null, null, "b", 10),
            )
        }
        assertEquals(0, f.leases())
        history.after = { throw InterruptedException("fictional interrupted history") }
        assertThrows(InterruptedException::class.java) {
            command.roomContext(
                f.tokens.accessToken,
                UUID.randomUUID(),
                f.boundRoom,
                "\$original",
                0,
            )
        }
        assertEquals(0, f.leases())
        assertEquals(2, history.calls.size)
    }

    @Test
    fun lateHistoryCannotExtendTheOriginalAdmissionDeadline() {
        val f = ready()
        val history = History(f)
        var offsetSeconds = 0L
        val clock =
            object : java.time.Clock() {
                override fun getZone(): java.time.ZoneId = java.time.ZoneOffset.UTC

                override fun withZone(zone: java.time.ZoneId): java.time.Clock = this

                override fun instant(): Instant = Instant.now().plusSeconds(offsetSeconds)
            }
        val command = f.command(f.provider(), clock = clock, roomHistory = history)
        history.after = { offsetSeconds = 31 }
        val result =
            command.roomEvent(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, "\$original")
        assertEquals(
            Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"),
            (result as Result.Failed).failure,
        )
        assertEquals(1, history.calls.size)
        assertEquals(0, f.leases())
    }

    private fun ready() = SpaceKeyExchangeFixture(postgres).also { it.bind() }

    private class History(val f: SpaceKeyExchangeFixture) :
        CollaborationSpaceMatrixRoomHistoryRepository by dev.fajar.hris.collaboration.data
            .repositories
            .DisabledCollaborationSpaceMatrixRoomHistoryRepository() {
        val calls = mutableListOf<String>()
        var page: CollaborationRoomPageRequest? = null
        var eventId: String? = null
        var after: () -> Unit = {}
        var failure: Failure? = null

        private fun read(
            kind: String,
            owner: CollaborationSpaceHistoryOwner,
            room: CollaborationSpaceBoundRoom,
            bearer: String,
            deadline: Instant,
        ): Result<CollaborationRoomHistory> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            assertEquals(f.s.account, owner.accountId)
            assertEquals(f.s.space, owner.spaceId)
            assertEquals(f.s.device, owner.deviceId)
            assertEquals(f.s.user, owner.matrixUserId)
            assertEquals(f.ownKeys.deviceId, owner.matrixDeviceId)
            assertNull(owner.legacy)
            assertEquals(f.boundRoom, room.binding.roomId)
            assertEquals("fixture-owned-query-token", bearer)
            assertTrue(deadline > Instant.now() && deadline <= Instant.now().plusSeconds(30))
            assertTrue(f.leases() in 1..7)
            calls += kind
            after()
            return failure?.let { Result.Failed(it) }
                ?: Result.Success(CollaborationRoomHistory(mapOf("chunk" to emptyList<Any>())))
        }

        override fun messages(
            owner: CollaborationSpaceHistoryOwner,
            room: CollaborationSpaceBoundRoom,
            request: CollaborationRoomPageRequest,
            bearer: String,
            deadline: Instant,
        ): Result<CollaborationRoomHistory> {
            page = request
            return read("messages", owner, room, bearer, deadline)
        }

        override fun context(
            owner: CollaborationSpaceHistoryOwner,
            room: CollaborationSpaceBoundRoom,
            eventId: String,
            limit: Int,
            bearer: String,
            deadline: Instant,
            filter: CollaborationRoomHistoryFilter,
        ): Result<CollaborationRoomHistory> {
            this.eventId = eventId
            return read("context", owner, room, bearer, deadline)
        }

        override fun event(
            owner: CollaborationSpaceHistoryOwner,
            room: CollaborationSpaceBoundRoom,
            eventId: String,
            bearer: String,
            deadline: Instant,
        ): Result<CollaborationRoomHistory> {
            this.eventId = eventId
            return read("event", owner, room, bearer, deadline)
        }
    }

    private fun failed(value: Result<*>) = (value as Result.Failed).failure.code

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            value,
            (value as? Result.Failed)?.failure?.code,
        )
        return (value as Result.Success).value
    }
}
