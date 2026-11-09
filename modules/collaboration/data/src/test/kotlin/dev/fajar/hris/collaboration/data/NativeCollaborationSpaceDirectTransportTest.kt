package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.ReadNativeCollaborationSpaceDirectTransport
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceDirectTransportTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun exactPublishedPairAndDeviceAreReadWithoutCreatingRoomOrRecipientState() {
        val f = SpaceKeyExchangeFixture(postgres)
        assertEquals("collaboration_room_not_ready", failure(read(f)))
        f.bind()
        // The pre-V122 fixture deliberately has no operational provisioning history.
        assertEquals("collaboration_device_unavailable", failure(read(f)))
        completeProvisioning(f)
        val before = f.provenance()
        val first = success(read(f))
        assertEquals(before, f.provenance())
        assertEquals(f.s.space, first.spaceId)
        assertEquals(f.s.account, first.accountId)
        assertEquals(f.s.parent, first.nativeSessionId)
        assertEquals(0L, first.nativeSessionVersion)
        assertEquals(f.conversation, first.conversation.id)
        assertEquals(
            f.pair,
            listOf(first.conversation.firstAccountId, first.conversation.secondAccountId),
        )
        assertEquals(f.s.device, first.device.id)
        assertEquals(f.s.user, first.matrixUserId)
        assertEquals(f.ownKeys.deviceId, first.matrixDeviceId)
        assertEquals(f.boundRoom, first.room.roomId)
        assertNull(first.room.original)
        assertFalse(first.recipientReady)
        f.reserve(f.peer, confirmed = false)
        assertFalse(success(read(f)).recipientReady)
        f.confirmPeer()
        val published = f.provenance()
        assertTrue(success(read(f)).recipientReady)
        assertEquals(published, f.provenance())
        assertEquals(0, f.leases())
    }

    @Test
    fun confirmedReceiverRemainsReadyAfterShortTokenExpiryButNotOffboarding() {
        val f = provisionedFixture()
        f.bind()
        f.reserve(f.peer)
        f.s.owner.update(
            "update collaboration_space_provider_sessions set generation=generation+1,updated_at=statement_timestamp(),token_expires_at=statement_timestamp()+interval '1 second' where device_id=?",
            f.peerDevice,
        )
        TimeUnit.MILLISECONDS.sleep(1100)
        f.s.owner.update(
            "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id=?",
            f.peerParent,
        )
        val retained = f.provenance()
        assertTrue(success(read(f)).recipientReady)
        assertEquals(retained, f.provenance())
        f.revokePeer()
        assertEquals("collaboration_peer_unavailable", failure(read(f)))
        assertEquals(0, f.leases())
    }

    @Test
    fun currentDenialOverridesBothSuccessfulAndFailedRecipientAcquisition() {
        val f = provisionedFixture()
        f.bind()
        f.reserve(f.peer)
        var denied = false
        var fail = false
        val spaces =
            object : CollaborationSpaceRepository by f.base.spaces {
                override fun current(): Result<UUID?> =
                    if (denied) Result.Success(null) else f.base.spaces.current()
            }
        val recipients =
            object : CollaborationSpaceRecipientRepository by f.recipients {
                override fun devices(
                    spaceId: UUID,
                    accountIds: Set<UUID>,
                    at: Instant,
                ): Result<List<CollaborationSpaceRecipientDevice>> {
                    val observed = f.recipients.devices(spaceId, accountIds, at)
                    denied = true
                    return if (fail)
                        Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_read_failed"))
                    else observed
                }
            }
        val before = f.provenance()
        for (failed in listOf(false, true)) {
            denied = false
            fail = failed
            assertEquals(
                "collaboration_access_denied",
                failure(read(f, recipients = recipients, spaces = spaces)),
            )
        }
        assertEquals(before, f.provenance())
        assertEquals(0, f.leases())
    }

    @Test
    fun peerRemovalAfterAcquisitionCannotPublishReadiness() {
        val f = provisionedFixture()
        f.bind()
        f.reserve(f.peer)
        val recipients =
            object : CollaborationSpaceRecipientRepository by f.recipients {
                override fun devices(
                    spaceId: UUID,
                    accountIds: Set<UUID>,
                    at: Instant,
                ): Result<List<CollaborationSpaceRecipientDevice>> {
                    val observed = f.recipients.devices(spaceId, accountIds, at)
                    f.revokePeer()
                    return observed
                }
            }
        assertEquals("collaboration_peer_unavailable", failure(read(f, recipients = recipients)))
        assertEquals(0, f.leases())
    }

    @Test
    fun elapsedOperationAndWrongDeviceCannotPublishCapturedMetadata() {
        val f = provisionedFixture()
        f.bind()
        f.reserve(f.peer)
        val now = AtomicReference(Instant.now())
        val clock =
            object : Clock() {
                override fun instant() = now.get()

                override fun getZone() = ZoneOffset.UTC

                override fun withZone(zone: ZoneId): Clock = this
            }
        val recipients =
            object : CollaborationSpaceRecipientRepository by f.recipients {
                override fun devices(
                    spaceId: UUID,
                    accountIds: Set<UUID>,
                    at: Instant,
                ): Result<List<CollaborationSpaceRecipientDevice>> {
                    val observed = f.recipients.devices(spaceId, accountIds, at)
                    now.set(now.get().plusSeconds(31))
                    return observed
                }
            }
        assertEquals(
            "collaboration_admission_changed",
            failure(read(f, recipients = recipients, clock = clock)),
        )
        val registry =
            object : CollaborationSpaceDeviceRepository by f.base.registry {
                override fun findBound(
                    spaceId: UUID,
                    accountId: UUID,
                    nativeSessionId: UUID,
                ): Result<CollaborationSpaceDevice?> =
                    f.base.registry.findBound(spaceId, accountId, nativeSessionId).map {
                        it?.copy(accountId = f.peer)
                    }
            }
        assertEquals("collaboration_device_unavailable", failure(read(f, registry = registry)))
        assertEquals(0, f.leases())
    }

    @Test
    fun malformedPairAndForeignGatewayCannotBecomeAnOwnedTransport() {
        val f = provisionedFixture()
        f.bind()
        val rows =
            StoredCollaborationSpaceConversationRepository(
                PostgresCollaborationSpaceConversationDataSource(f.base.sql)
            )
        for (invalid in
            listOf<(CollaborationSpaceDirectConversation) -> CollaborationSpaceDirectConversation>(
                { it.copy(version = 1) },
                { it.copy(createdBy = UUID.randomUUID()) },
                { it.copy(createdAt = Instant.now().plusSeconds(60)) },
                {
                    it.copy(
                        firstAccountId = it.secondAccountId,
                        secondAccountId = it.firstAccountId,
                    )
                },
            )) {
            val conversations =
                object : CollaborationSpaceConversationRepository by rows {
                    override fun find(
                        spaceId: UUID,
                        accountId: UUID,
                        id: UUID,
                    ): Result<CollaborationSpaceDirectConversation?> =
                        rows.find(spaceId, accountId, id).map { it?.let(invalid) }
                }
            assertEquals(
                "collaboration_conversation_not_found",
                failure(read(f, conversations = conversations)),
            )
        }
        val gateway =
            object : CollaborationSpaceGatewaySessionRepository by f.s.repo {
                override fun find(
                    spaceId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                    lock: Boolean,
                ): Result<CollaborationSpaceGatewaySession?> =
                    f.s.repo.find(spaceId, accountId, deviceId, lock).map {
                        it?.copy(accountId = f.peer)
                    }
            }
        assertEquals("invalid_collaboration_space_gateway", failure(read(f, gateway = gateway)))
        assertEquals(0, f.leases())
    }

    @Test
    fun cancellationAndInterruptionReleaseTheReadTransactionWithoutChangingState() {
        val f = provisionedFixture()
        f.bind()
        f.reserve(f.peer)
        val before = f.provenance()
        for (cancel in listOf(true, false)) {
            val recipients =
                object : CollaborationSpaceRecipientRepository by f.recipients {
                    override fun devices(
                        spaceId: UUID,
                        accountIds: Set<UUID>,
                        at: Instant,
                    ): Result<List<CollaborationSpaceRecipientDevice>> {
                        if (cancel)
                            throw CancellationException("fictional direct read cancellation")
                        throw InterruptedException("fictional direct read interruption")
                    }
                }
            if (cancel)
                assertThrows(CancellationException::class.java) { read(f, recipients = recipients) }
            else assertThrows(InterruptedException::class.java) { read(f, recipients = recipients) }
            assertEquals(before, f.provenance())
            assertEquals(0, f.leases())
            assertTrue(success(read(f)).recipientReady)
        }
    }

    private fun provisionedFixture() =
        SpaceKeyExchangeFixture(postgres).also { completeProvisioning(it) }

    private fun completeProvisioning(f: SpaceKeyExchangeFixture) {
        val requested = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val request =
            CollaborationSpaceProvisioningRequest(
                f.s.space,
                f.s.account,
                f.s.device,
                f.s.parent,
                0,
                0,
                0,
                f.s.actor.authenticatedAt,
                null,
                f.s.actor.correlationId,
                f.base.client,
                requested,
                requested.plusSeconds(60),
            )
        success(f.s.tx { f.base.provisioning.enqueue(request) })
        val worker =
            StoredCollaborationSpaceProvisioningRepository(
                PostgresCollaborationSpaceProvisioningDataSource(
                    DSL.using(
                        TransactionAwareDataSourceProxy(f.s.workerSource),
                        SQLDialect.POSTGRES,
                    )
                )
            )
        val actor = f.s.actor.copy(accountId = UUID(0, 0))
        val claimed =
            success(f.workerTransactions.run(actor) { worker.claim(UUID.randomUUID(), 1, 30) })
                .single()
        assertTrue(
            success(
                f.workerTransactions.run(actor) { worker.finish(requireNotNull(claimed.lease)) }
            )
        )
    }

    private fun read(
        f: SpaceKeyExchangeFixture,
        recipients: CollaborationSpaceRecipientRepository = f.recipients,
        spaces: CollaborationSpaceRepository = f.base.spaces,
        registry: CollaborationSpaceDeviceRepository = f.base.registry,
        clock: Clock = Clock.systemUTC(),
        conversations: CollaborationSpaceConversationRepository =
            StoredCollaborationSpaceConversationRepository(
                PostgresCollaborationSpaceConversationDataSource(f.base.sql)
            ),
        gateway: CollaborationSpaceGatewaySessionRepository = f.s.repo,
    ) =
        ReadNativeCollaborationSpaceDirectTransport(
                StoredCollaborationSpaceRoomRepository(
                    PostgresCollaborationSpaceRoomDataSource(f.base.sql)
                ),
                conversations,
                registry,
                f.base.provisioning,
                gateway,
                recipients,
                spaces,
                f.base.sessions,
                f.base.identities,
                f.s.transactions,
                CollaborationSpacePolicy(true, 5, 5),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                IdentitySecurityPolicy(),
                clock,
            )
            .execute(f.s.actor, f.s.parent, 0, f.s.space, f.conversation, f.base.client)

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }

    private fun failure(value: Result<*>): String {
        assertInstanceOf(Result.Failed::class.java, value)
        return (value as Result.Failed).failure.code
    }
}
