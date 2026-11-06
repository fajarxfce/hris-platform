package dev.fajar.hris.collaboration.data

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
class NativeCollaborationSpaceToDeviceTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun exactConfirmedCrossCompanyRecipientsKeepOriginalTransactionAndCiphertextAcrossRetry() {
        val f = ready()
        val keys = f.provider()
        val sender = Sender(f)
        val request = request(f)
        fun send() =
            f.command(keys, toDevice = sender)
                .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), request)
        val before = f.provenance()
        sender.failure = Failure(FailureKind.UNAVAILABLE, "fixture_lost_send")
        assertEquals("fixture_lost_send", failed(send()))
        sender.failure = null
        success(send())
        assertEquals(listOf(request, request), sender.received)
        assertEquals(2, keys.ownReads.get())
        assertEquals(2, keys.queries.get())
        assertEquals(before, f.provenance())
        assertEquals(0, f.leases())
    }

    @Test
    fun oneIneligibleOrUnknownRecipientRejectsTheWholeEnvelopeBeforeProviderIo() {
        val f = ready()
        val keys = f.provider()
        val sender = Sender(f)
        val valid = request(f)
        val unknown = f.user(UUID.randomUUID())
        val mixed =
            valid.copy(messages = valid.messages + (unknown to valid.messages.values.first()))
        assertEquals(
            "collaboration_recipient_unavailable",
            failed(
                f.command(keys, toDevice = sender)
                    .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), mixed)
            ),
        )
        f.revokePeer()
        assertEquals(
            "collaboration_recipient_unavailable",
            failed(
                f.command(keys, toDevice = sender)
                    .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), valid)
            ),
        )
        assertEquals(0, keys.ownReads.get())
        assertEquals(0, keys.queries.get())
        assertTrue(sender.received.isEmpty())
        assertEquals(0, f.leases())
    }

    @Test
    fun pendingOwnKeysAreRetryableAndPendingRecipientCannotReceive() {
        val f = SpaceKeyExchangeFixture(postgres)
        f.reserve(confirmed = false)
        f.reserve(f.peer, confirmed = false)
        f.bind()
        val keys = f.provider()
        val sender = Sender(f)
        fun send() =
            f.command(keys, toDevice = sender)
                .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), request(f))
        assertEquals("collaboration_recipient_unavailable", failed(send()))
        f.confirmPeer()
        assertEquals("collaboration_device_keys_pending", failed(send()))
        assertEquals(0, keys.ownReads.get())
        assertTrue(sender.received.isEmpty())
        success(
            f.s.tx {
                f.keys.find(f.s.space, f.s.account, f.s.device).flatMap {
                    f.keys.publish(requireNotNull(it), f.now())
                }
            }
        )
        success(send())
        assertEquals(1, sender.received.size)
        assertEquals(0, f.leases())
    }

    @Test
    fun receiverChangesBetweenKeyVerificationAndSendOrAfterEitherSendOutcomeWithholdSuccess() {
        val f = ready()
        val firstKeys = f.provider()
        val firstSender = Sender(f)
        firstKeys.onQuery = { f.revokePeer() }
        assertEquals(
            "collaboration_recipients_changed",
            failed(
                f.command(firstKeys, toDevice = firstSender)
                    .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), request(f))
            ),
        )
        assertTrue(firstSender.received.isEmpty())
        f.grantPeer()
        for (succeeds in listOf(false, true)) {
            val keys = f.provider()
            val sender = Sender(f)
            sender.after = { f.revokePeer() }
            if (!succeeds) sender.failure = Failure(FailureKind.UNAVAILABLE, "fixture_lost_send")
            assertEquals(
                "collaboration_recipients_changed",
                failed(
                    f.command(keys, toDevice = sender)
                        .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), request(f))
                ),
            )
            // The remote side may already have accepted ciphertext; only the stale success is
            // withheld.
            assertEquals(1, sender.received.size)
            assertEquals(0, f.leases())
            f.grantPeer()
        }
    }

    @Test
    fun senderIdentityAndBothEnvelopeCurveKeysMustMatchBeforeAnyDelivery() {
        val f = ready()
        val keys = f.provider()
        val sender = Sender(f)
        val valid = request(f)
        keys.materials[f.ownKeys.userId] = MatrixKeyFixture(f.ownKeys.userId, f.ownKeys.deviceId)
        assertEquals(
            "collaboration_space_device_keys_changed",
            failed(
                f.command(keys, toDevice = sender)
                    .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), valid)
            ),
        )
        keys.materials[f.ownKeys.userId] = f.ownKeys
        val envelope = valid.messages.values.single().values.single()
        for (invalid in
            listOf(
                envelope.copy(senderKey = envelope.recipientKey),
                envelope.copy(recipientKey = envelope.senderKey),
            )) {
            val wrong =
                valid.copy(
                    messages = mapOf(f.peerKeys.userId to mapOf(f.peerKeys.deviceId to invalid))
                )
            assertEquals(
                "collaboration_encryption_keys_changed",
                failed(
                    f.command(keys, toDevice = sender)
                        .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), wrong)
                ),
            )
        }
        assertTrue(sender.received.isEmpty())
        assertEquals(0, f.leases())
    }

    @Test
    fun callerAndProviderMapMutationCannotReplaceAdmittedCiphertextOrVerifiedSender() {
        val f = ready()
        val sender = Sender(f)
        val expected = request(f)
        val devices = expected.messages.values.single().toMutableMap()
        val users = mutableMapOf(f.peerKeys.userId to devices)
        val mutable = expected.copy(messages = users)
        val algorithms = f.ownKeys.device.algorithms.toMutableList()
        val publicKeys = f.ownKeys.device.keys.toMutableMap()
        val signatures = f.ownKeys.device.signatures.getValue(f.ownKeys.userId).toMutableMap()
        val identity =
            f.ownKeys.device.copy(
                algorithms = algorithms,
                keys = publicKeys,
                signatures = mapOf(f.ownKeys.userId to signatures),
            )
        val base = f.provider()
        base.onQuery = {
            algorithms.clear()
            publicKeys.clear()
            signatures.clear()
        }
        val keys =
            object : CollaborationMatrixKeyRepository by base {
                override fun ownDeviceKeys(
                    userId: String,
                    deviceId: String,
                    token: String,
                    deadline: Instant,
                ): Result<CollaborationMatrixDeviceKeys?> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
                    assertEquals(f.ownKeys.userId, userId)
                    assertEquals(f.ownKeys.deviceId, deviceId)
                    assertEquals("fixture-owned-query-token", token)
                    users.clear()
                    devices.clear()
                    return Result.Success(identity)
                }
            }
        success(
            f.command(keys, toDevice = sender)
                .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), mutable)
        )
        assertEquals(expected, sender.received.single())
        assertEquals(0, f.leases())
    }

    @Test
    fun callerGenerationChangesAfterRemoteAcceptanceWithholdTheResponse() {
        val f = ready()
        for (native in listOf(false, true)) {
            val sender = Sender(f)
            sender.after = {
                if (native)
                    f.s.owner.update(
                        "update native_sessions set version=version+1 where id=?",
                        f.s.parent,
                    )
                else f.rotateProvider()
            }
            assertEquals(
                "collaboration_admission_changed",
                failed(
                    f.command(f.provider(), toDevice = sender)
                        .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), request(f))
                ),
            )
            assertEquals(1, sender.received.size)
            assertEquals(0, f.leases())
        }
    }

    @Test
    fun keyReadAndSendCancellationPropagateAndReleaseTheOwnedRequest() {
        val f = ready()
        val keys = f.provider()
        val sender = Sender(f)
        keys.onOwn = { throw CancellationException("fictional own-key cancellation") }
        assertThrows(CancellationException::class.java) {
            f.command(keys, toDevice = sender)
                .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), request(f))
        }
        assertTrue(sender.received.isEmpty())
        assertEquals(0, f.leases())
        keys.onOwn = {}
        sender.after = { throw InterruptedException("fictional send interruption") }
        assertThrows(InterruptedException::class.java) {
            f.command(keys, toDevice = sender)
                .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), request(f))
        }
        assertEquals(1, sender.received.size)
        assertEquals(0, f.leases())
    }

    @Test
    fun actualProviderQueuesOfflineEnvelopeAndPreservesOriginalReplayIdentityAcrossRestart() {
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
            fun sync(since: String? = null): tools.jackson.databind.JsonNode {
                val suffix =
                    since?.let {
                        "&since=" +
                            java.net.URLEncoder.encode(it, java.nio.charset.StandardCharsets.UTF_8)
                    } ?: ""
                val response =
                    provider.client.send(
                        java.net.http.HttpRequest.newBuilder(
                                provider.config.origin.resolve(
                                    "/_matrix/client/v3/sync?timeout=0$suffix"
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
                return provider.json.readTree(response.body())
            }
            val before = sync().path("next_batch").asString()
            f.s.owner.update(
                "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id=?",
                f.peerParent,
            )
            val input = request(f)
            val provenance = f.provenance()
            fun send() =
                f.command(
                        dev.fajar.hris.collaboration.data.repositories
                            .InternalCollaborationMatrixKeyRepository(raw()),
                        toDevice =
                            dev.fajar.hris.collaboration.data.repositories
                                .InternalCollaborationMatrixToDeviceRepository(raw()),
                    )
                    .sendToDevice(f.tokens.accessToken, UUID.randomUUID(), input)
            repeat(2) { success(send()) }
            val received = sync(before)
            val events = received.path("to_device").path("events")
            assertEquals(1, events.size())
            assertEquals(own.userId, events[0].path("sender").asString())
            assertEquals("m.room.encrypted", events[0].path("type").asString())
            val content = events[0].path("content")
            val envelope = input.messages.values.single().values.single()
            assertEquals(envelope.messageId, content.path("org.matrix.msgid").asString())
            assertEquals(
                envelope.body,
                content.path("ciphertext").path(envelope.recipientKey).path("body").asString(),
            )
            val acknowledged = sync(received.path("next_batch").asString())
            assertTrue(acknowledged.path("to_device").path("events").isEmpty)
            provider.restart()
            success(send())
            val replay =
                sync(acknowledged.path("next_batch").asString()).path("to_device").path("events")
            assertEquals(1, replay.size())
            assertEquals(content, replay[0].path("content"))
            assertEquals(provenance, f.provenance())
            assertEquals(0, f.leases())
            // Process-local provider transaction caching allows replay after restart. This is
            // opaque transport evidence; valid Olm decryption and SDK replay handling are separate.
        }
    }

    private fun ready() =
        SpaceKeyExchangeFixture(postgres).also {
            it.reserve()
            it.reserve(it.peer)
            it.bind()
        }

    private fun request(f: SpaceKeyExchangeFixture): CollaborationToDevice {
        val envelope =
            CollaborationOlmEnvelope(
                f.ownKeys.device.keys.getValue("curve25519:${f.ownKeys.deviceId}"),
                f.peerKeys.device.keys.getValue("curve25519:${f.peerKeys.deviceId}"),
                "ZmljdGlvbmFsLW9wYXF1ZS1jaXBoZXJ0ZXh0",
                0,
                "original-sdk-message",
            )
        return CollaborationToDevice(
            "original-sdk-transaction",
            mapOf(f.peerKeys.userId to mapOf(f.peerKeys.deviceId to envelope)),
        )
    }

    private class Sender(val f: SpaceKeyExchangeFixture) : CollaborationMatrixToDeviceRepository {
        val received = mutableListOf<CollaborationToDevice>()
        var failure: Failure? = null
        var after: () -> Unit = {}

        override fun send(
            request: CollaborationToDevice,
            token: String,
            deadline: Instant,
        ): Result<Unit> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            assertEquals("fixture-owned-query-token", token)
            assertTrue(f.leases() in 1..7)
            assertTrue(deadline <= Instant.now().plusSeconds(30))
            received += request
            after()
            return failure?.let { Result.Failed(it) } ?: Result.Success(Unit)
        }
    }

    private fun failed(result: Result<*>) = (result as Result.Failed).failure.code

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
