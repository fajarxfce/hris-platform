package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationSpaceRoomDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceRoomRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceRoomMessageTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun exactRoomKeepsOriginalCiphertextSessionAndTransactionAcrossLostResponseRetry() {
        val f = ready()
        val sender = Sender(f)
        val keys = f.provider()
        val input = message(f)
        fun send() =
            f.command(keys, roomMessages = sender)
                .sendRoomMessage(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, input)
        val before = f.provenance()
        sender.failure = Failure(FailureKind.UNAVAILABLE, "fixture_room_response_lost")
        assertEquals("fixture_room_response_lost", failed(send()))
        sender.failure = null
        assertEquals("\$fixture-group-event", success(send()))
        assertEquals(listOf(f.boundRoom to input, f.boundRoom to input), sender.received)
        assertEquals(2, keys.ownReads.get())
        assertEquals(before, f.provenance())
        assertEquals(0, f.leases())
    }

    @Test
    fun unboundAndUnknownRoomsNeverReachTheProvider() {
        val f = SpaceKeyExchangeFixture(postgres)
        f.reserve()
        f.reserve(f.peer)
        val sender = Sender(f)
        val keys = f.provider()
        fun send(room: String) =
            f.command(keys, roomMessages = sender)
                .sendRoomMessage(f.tokens.accessToken, UUID.randomUUID(), room, message(f))
        assertEquals("collaboration_room_not_found", failed(send(f.boundRoom)))
        f.bind()
        assertEquals("collaboration_room_not_found", failed(send("!unknown:matrix.example.test")))
        assertEquals(0, keys.ownReads.get())
        assertTrue(sender.received.isEmpty())
        assertEquals(0, f.leases())
    }

    @Test
    fun offlinePeerRemainsEligibleUntilCurrentMembershipIsRevoked() {
        val f = ready()
        val sender = Sender(f)
        val keys = f.provider()
        f.s.owner.update(
            "update collaboration_space_provider_sessions set generation=generation+1,updated_at=statement_timestamp(),token_expires_at=statement_timestamp()+interval '1 second' where device_id=?",
            f.peerDevice,
        )
        TimeUnit.MILLISECONDS.sleep(1100)
        f.s.owner.update(
            "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id=?",
            f.peerParent,
        )
        fun send() =
            f.command(keys, roomMessages = sender)
                .sendRoomMessage(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, message(f))
        success(send())
        f.revokePeer()
        assertEquals("collaboration_recipient_unavailable", failed(send()))
        assertEquals(1, sender.received.size)
        assertEquals(0, f.leases())
    }

    @Test
    fun receiverChangesBeforeDeliveryOrAfterEitherRemoteOutcomeWithholdStaleResults() {
        val f = ready()
        val keys = f.provider()
        val firstSender = Sender(f)
        keys.onOwn = { f.revokePeer() }
        assertEquals(
            "collaboration_recipients_changed",
            failed(
                f.command(keys, roomMessages = firstSender)
                    .sendRoomMessage(
                        f.tokens.accessToken,
                        UUID.randomUUID(),
                        f.boundRoom,
                        message(f),
                    )
            ),
        )
        assertTrue(firstSender.received.isEmpty())
        f.grantPeer()
        keys.onOwn = {}
        for (succeeds in listOf(false, true)) {
            val sender = Sender(f)
            sender.after = { f.revokePeer() }
            if (!succeeds)
                sender.failure = Failure(FailureKind.UNAVAILABLE, "fixture_room_send_lost")
            assertEquals(
                "collaboration_recipients_changed",
                failed(
                    f.command(keys, roomMessages = sender)
                        .sendRoomMessage(
                            f.tokens.accessToken,
                            UUID.randomUUID(),
                            f.boundRoom,
                            message(f),
                        )
                ),
            )
            assertEquals(1, sender.received.size)
            assertEquals(0, f.leases())
            f.grantPeer()
        }
    }

    @Test
    fun explicitDeviceAndSenderCurveMustMatchTheVerifiedOriginalIdentity() {
        val f = ready()
        val keys = f.provider()
        val sender = Sender(f)
        val input = message(f)
        for (invalid in
            listOf(
                input.copy(deviceId = f.peerKeys.deviceId),
                input.copy(
                    senderKey = f.peerKeys.device.keys.getValue("curve25519:${f.peerKeys.deviceId}")
                ),
            )) {
            assertEquals(
                "collaboration_encryption_keys_changed",
                failed(
                    f.command(keys, roomMessages = sender)
                        .sendRoomMessage(
                            f.tokens.accessToken,
                            UUID.randomUUID(),
                            f.boundRoom,
                            invalid,
                        )
                ),
            )
        }
        keys.materials[f.ownKeys.userId] = MatrixKeyFixture(f.ownKeys.userId, f.ownKeys.deviceId)
        assertEquals(
            "collaboration_space_device_keys_changed",
            failed(
                f.command(keys, roomMessages = sender)
                    .sendRoomMessage(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, input)
            ),
        )
        assertTrue(sender.received.isEmpty())
        assertEquals(0, f.leases())
        keys.materials[f.ownKeys.userId] = f.ownKeys
        success(
            f.command(keys, roomMessages = sender)
                .sendRoomMessage(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.boundRoom,
                    input.copy(senderKey = null, deviceId = null),
                )
        )
        assertEquals(1, sender.received.size) // Current Matrix SDK may omit these optional fields.
    }

    @Test
    fun failedRoomDiscoveryStillAdmitsCurrentCallerBeforeReturningLookupFailure() {
        val f = ready()
        val keys = f.provider()
        val sender = Sender(f)
        val real =
            StoredCollaborationSpaceRoomRepository(
                PostgresCollaborationSpaceRoomDataSource(f.base.sql)
            )
        val rooms =
            object : CollaborationSpaceRoomRepository by real {
                override fun boundRoom(
                    spaceId: UUID,
                    accountId: UUID,
                    serverName: String,
                    roomId: String,
                ): Result<CollaborationSpaceBoundRoom?> =
                    Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_lookup_failed"))
            }
        fun send() =
            f.command(keys, rooms = rooms, roomMessages = sender)
                .sendRoomMessage(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, message(f))
        assertEquals("fixture_lookup_failed", failed(send()))
        f.s.owner.update(
            "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
            f.s.parent,
        )
        assertEquals(FailureKind.UNAUTHENTICATED, (send() as Result.Failed).failure.kind)
        assertTrue(sender.received.isEmpty())
        assertEquals(0, keys.ownReads.get())
        assertEquals(0, f.leases())
    }

    @Test
    fun keyAndSendCancellationPreserveOriginalFailureAndReleaseTheExactSlot() {
        val f = ready()
        val keys = f.provider()
        val sender = Sender(f)
        fun send() =
            f.command(keys, roomMessages = sender)
                .sendRoomMessage(f.tokens.accessToken, UUID.randomUUID(), f.boundRoom, message(f))
        keys.onOwn = { throw CancellationException("fictional sender key cancellation") }
        assertThrows(CancellationException::class.java) { send() }
        assertTrue(sender.received.isEmpty())
        assertEquals(0, f.leases())
        keys.onOwn = {}
        sender.after = { throw InterruptedException("fictional room send interruption") }
        assertThrows(InterruptedException::class.java) { send() }
        assertEquals(1, sender.received.size)
        assertEquals(0, f.leases())
    }

    @Test
    fun actualProviderRetainsOriginalRoomEventAcrossRetryAndRestartForOfflinePeer() {
        MatrixProviderFixture("10m").use { provider ->
            provider.start()
            val f = SpaceKeyExchangeFixture(postgres)
            fun raw() =
                dev.fajar.hris.collaboration.data.matrix.HttpMatrixClientDataSource(
                    provider.client,
                    provider.config,
                    provider.clock,
                )
            fun deadline() = Instant.now().plusSeconds(30)
            val group =
                dev.fajar.hris.collaboration.data.repositories
                    .InternalCollaborationSpaceMatrixRepository(
                        dev.fajar.hris.collaboration.data.matrix.HmacMatrixJwtDataSource(
                            provider.config
                        ),
                        raw(),
                        provider.clock,
                    )
            fun login(account: UUID, device: UUID): CollaborationMatrixCredential {
                val at = Instant.now()
                return success(
                    group.login(
                        CollaborationSpaceMatrixLogin(
                            f.s.space,
                            account,
                            device,
                            null,
                            null,
                            at,
                            at.plusSeconds(30),
                        )
                    )
                )
            }
            val own = login(f.s.account, f.s.device)
            val peer = login(f.peer, f.peerDevice)
            val keys =
                dev.fajar.hris.collaboration.data.repositories
                    .InternalCollaborationMatrixKeyRepository(raw())
            for ((credential, material) in listOf(own to f.ownKeys, peer to f.peerKeys)) success(
                keys.upload(
                    CollaborationMatrixKeyUpload(material.device),
                    credential.accessToken,
                    deadline(),
                )
            )
            f.rotateProvider(own.accessToken, own.expiresAt)
            f.reserve()
            f.reserve(f.peer)
            val rooms =
                dev.fajar.hris.collaboration.data.repositories
                    .InternalCollaborationSpaceMatrixRoomRepository(
                        raw(),
                        provider.config.serverName,
                    )
            val room =
                success(
                    rooms.create(
                        CollaborationSpaceMatrixDirectRoom(
                            f.s.space,
                            f.conversation,
                            f.s.account,
                            f.peer,
                            own.userId,
                            peer.userId,
                        ),
                        own.accessToken,
                        deadline(),
                    )
                )
                    as CollaborationMatrixRoomCreation.Created
            success(rooms.join(room.roomId, peer.accessToken, deadline()))
            f.bind(room.roomId)
            f.s.owner.update(
                "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id=?",
                f.peerParent,
            )
            val input = message(f)
            val provenance = f.provenance()
            fun send() =
                f.command(
                        // The owned container may receive a new mapped port on restart.
                        dev.fajar.hris.collaboration.data.repositories
                            .InternalCollaborationMatrixKeyRepository(raw()),
                        roomMessages =
                            dev.fajar.hris.collaboration.data.repositories
                                .InternalCollaborationMatrixRoomMessageRepository(raw()),
                    )
                    .sendRoomMessage(f.tokens.accessToken, UUID.randomUUID(), room.roomId, input)
            val event = success(send())
            assertEquals(event, success(send()))
            provider.restart()
            assertEquals(event, success(send()))
            val encodedRoom =
                java.net.URLEncoder.encode(room.roomId, java.nio.charset.StandardCharsets.UTF_8)
            val response =
                provider.client.send(
                    java.net.http.HttpRequest.newBuilder(
                            provider.config.origin.resolve(
                                "/_matrix/client/v3/rooms/$encodedRoom/messages?dir=b&limit=100"
                            )
                        )
                        .timeout(java.time.Duration.ofSeconds(5))
                        .header("Authorization", "Bearer ${peer.accessToken}")
                        .GET()
                        .build(),
                    java.net.http.HttpResponse.BodyHandler {
                        dev.fajar.hris.collaboration.data.matrix.BoundedMatrixBodySubscriber()
                    },
                )
            assertEquals(200, response.statusCode())
            val matching =
                provider.json.readTree(response.body()).path("chunk").filter {
                    it.path("event_id").asString() == event
                }
            assertEquals(1, matching.size)
            assertEquals(own.userId, matching.single().path("sender").asString())
            assertEquals("m.room.encrypted", matching.single().path("type").asString())
            assertEquals(
                input.ciphertext,
                matching.single().path("content").path("ciphertext").asString(),
            )
            assertEquals(
                input.sessionId,
                matching.single().path("content").path("session_id").asString(),
            )
            assertEquals(provenance, f.provenance())
            assertEquals(0, f.leases())
            f.revokePeer()
            val reads =
                f.command(
                    dev.fajar.hris.collaboration.data.repositories
                        .InternalCollaborationMatrixKeyRepository(raw()),
                    roomHistory =
                        dev.fajar.hris.collaboration.data.repositories
                            .InternalCollaborationSpaceMatrixRoomHistoryRepository(
                                raw(),
                                collaborationCursorFixture(),
                            ),
                )
            val history =
                success(
                    reads.roomMessages(
                        f.tokens.accessToken,
                        UUID.randomUUID(),
                        room.roomId,
                        CollaborationRoomPageRequest(null, null, "b", 100),
                    )
                )
            assertEquals(
                1,
                (history.payload["chunk"] as List<*>).count {
                    (it as Map<*, *>)["event_id"] == event
                },
            )
            assertEquals(
                event,
                success(
                        reads.roomEvent(f.tokens.accessToken, UUID.randomUUID(), room.roomId, event)
                    )
                    .payload["event_id"],
            )
            val context =
                success(
                    reads.roomContext(
                        f.tokens.accessToken,
                        UUID.randomUUID(),
                        room.roomId,
                        event,
                        0,
                    )
                )
            assertEquals(event, (context.payload["event"] as Map<*, *>)["event_id"])
            assertEquals(provenance, f.provenance())
            assertEquals(0, f.leases())
            // Actual opaque transport and provider transaction evidence, not SDK Megolm decryption.
        }
    }

    private fun ready() =
        SpaceKeyExchangeFixture(postgres).also {
            it.reserve()
            it.reserve(it.peer)
            it.bind()
        }

    private fun message(f: SpaceKeyExchangeFixture) =
        CollaborationRoomMessage(
            "original-sdk-room-transaction",
            "ZmljdGlvbmFsLW1lZ29sbS1jaXBoZXJ0ZXh0",
            Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 9 }),
            f.ownKeys.device.keys.getValue("curve25519:${f.ownKeys.deviceId}"),
            f.ownKeys.deviceId,
        )

    private class Sender(val f: SpaceKeyExchangeFixture) :
        CollaborationMatrixRoomMessageRepository {
        val received = mutableListOf<Pair<String, CollaborationRoomMessage>>()
        var failure: Failure? = null
        var after: () -> Unit = {}

        override fun send(
            roomId: String,
            message: CollaborationRoomMessage,
            bearer: String,
            deadline: Instant,
        ): Result<String> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            assertEquals("fixture-owned-query-token", bearer)
            assertTrue(deadline <= Instant.now().plusSeconds(30))
            assertTrue(f.leases() in 1..7)
            received += roomId to message
            after()
            return failure?.let { Result.Failed(it) } ?: Result.Success("\$fixture-group-event")
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
