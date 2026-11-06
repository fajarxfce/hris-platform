package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.*
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceKeyQueryTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun ordinaryRootGatewaySeesOnlyOwnKeysUntilExactCrossCompanyRoomIsBound() {
        val f = SpaceKeyExchangeFixture(postgres)
        f.reserve()
        f.reserve(f.peer)
        val provider = f.provider()
        val before = success(f.query(provider))
        assertEquals(f.ownKeys.device, before.deviceKeys[f.s.user]?.get(f.ownKeys.deviceId))
        assertTrue(before.deviceKeys.getValue(f.user(f.peer)).isEmpty())
        assertEquals(setOf(f.s.user), provider.received.single().devices.keys)
        f.bind()
        val after = success(f.query(provider))
        assertEquals(f.peerKeys.device, after.deviceKeys[f.user(f.peer)]?.get(f.peerKeys.deviceId))
        assertNull(success(f.s.tx { f.keys.find(f.s.space, f.peer, f.peerDevice) }))
        val wrongAlias =
            "@${collaborationMatrixLocalpart(UUID.randomUUID(),f.peer)}:matrix.example.test"
        val unknown = f.user(UUID.randomUUID())
        val request =
            CollaborationMatrixKeyQuery(
                f.queryRequest().devices + mapOf(wrongAlias to emptyList(), unknown to emptyList())
            )
        val mixed = success(f.query(provider, request = request))
        assertTrue(mixed.deviceKeys.getValue(wrongAlias).isEmpty())
        assertTrue(mixed.deviceKeys.getValue(unknown).isEmpty())
        assertEquals(f.peerKeys.device, mixed.deviceKeys[f.user(f.peer)]?.get(f.peerKeys.deviceId))
        assertEquals(0, f.leases())
    }

    @Test
    fun offlinePeerStillReceivesAfterShortNativeAndProviderCredentialsExpire() {
        val f = ready()
        val provider = f.provider()
        val before = f.provenance()
        f.s.owner.update(
            "update collaboration_space_provider_sessions set generation=generation+1,updated_at=statement_timestamp(),token_expires_at=statement_timestamp()+interval '1 second' where device_id=?",
            f.peerDevice,
        )
        TimeUnit.MILLISECONDS.sleep(1100)
        f.s.owner.update(
            "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id=?",
            f.peerParent,
        )
        assertEquals(
            f.peerKeys.device,
            success(f.query(provider)).deviceKeys[f.user(f.peer)]?.get(f.peerKeys.deviceId),
        )
        assertTrue(
            success(f.claimKeys(provider))
                .oneTimeKeys
                .getValue(f.user(f.peer))
                .getValue(f.peerKeys.deviceId)
                .isNotEmpty()
        )
        assertEquals(before, f.provenance())
        assertEquals(0, f.leases())
    }

    @Test
    fun pendingPublicationIsQueryableButCannotConsumeKeysAndAcknowledgementRaceRetries() {
        val f = SpaceKeyExchangeFixture(postgres)
        f.reserve()
        f.reserve(f.peer, confirmed = false)
        f.bind()
        val provider = f.provider()
        assertTrue(success(f.claimKeys(provider)).oneTimeKeys.getValue(f.user(f.peer)).isEmpty())
        assertEquals(0, provider.queries.get())
        assertEquals(0, provider.claims.get())
        assertEquals(
            f.peerKeys.device,
            success(f.query(provider)).deviceKeys[f.user(f.peer)]?.get(f.peerKeys.deviceId),
        )
        provider.onQuery = { f.confirmPeer() }
        assertEquals("collaboration_recipients_changed", failed(f.query(provider)))
        provider.onQuery = {}
        success(f.claimKeys(provider))
        assertEquals(1, provider.claims.get())
        assertEquals(0, f.leases())
    }

    @Test
    fun receiverRevocationDominatesSuccessfulAndFailedQueries() {
        val f = ready()
        for (succeeds in listOf(false, true)) {
            val provider = f.provider()
            provider.onQuery = { f.revokePeer() }
            if (!succeeds)
                provider.queryFailure = Failure(FailureKind.UNAVAILABLE, "fixture_provider_failure")
            assertEquals("collaboration_recipients_changed", failed(f.query(provider)))
            assertEquals(0, f.leases())
            f.grantPeer()
        }
    }

    @Test
    fun claimRechecksAfterIdentityVerificationAndAfterBothConsumptionOutcomes() {
        val f = ready()
        val interrupted = f.provider()
        interrupted.onQuery = { f.revokePeer() }
        assertEquals("collaboration_recipients_changed", failed(f.claimKeys(interrupted)))
        assertEquals(0, interrupted.claims.get())
        f.grantPeer()
        for (succeeds in listOf(false, true)) {
            val provider = f.provider()
            provider.onClaim = { f.revokePeer() }
            if (!succeeds)
                provider.claimFailure = Failure(FailureKind.UNAVAILABLE, "fixture_claim_lost")
            assertEquals("collaboration_recipients_changed", failed(f.claimKeys(provider)))
            assertEquals(1, provider.queries.get())
            assertEquals(1, provider.claims.get())
            assertEquals(0, f.leases())
            f.grantPeer()
        }
    }

    @Test
    fun callerProviderAndNativeChangesWithholdEitherProviderOutcome() {
        val f = ready()
        for (native in listOf(false, true)) for (succeeds in listOf(false, true)) {
            val provider = f.provider()
            provider.onQuery = {
                if (native)
                    f.s.owner.update(
                        "update native_sessions set version=version+1 where id=?",
                        f.s.parent,
                    )
                else f.rotateProvider()
            }
            if (!succeeds)
                provider.queryFailure = Failure(FailureKind.UNAVAILABLE, "fixture_query_lost")
            assertEquals("collaboration_admission_changed", failed(f.query(provider)))
            assertEquals(0, f.leases())
        }
    }

    @Test
    fun accountGuardsPrecedeNativeLocksAndEachCheckpointUsesOneBoundedProjection() {
        val f = ready()
        val order = mutableListOf<UUID>()
        val reads = AtomicInteger()
        val guarded =
            object : IdentityRepository by f.base.identities {
                override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> {
                    order += accountId
                    assertTrue(shared)
                    return f.base.identities.lockAccount(accountId, shared)
                }
            }
        val recipients =
            object : CollaborationSpaceRecipientRepository by f.recipients {
                override fun devices(
                    spaceId: UUID,
                    accountIds: Set<UUID>,
                    at: Instant,
                ): Result<List<CollaborationSpaceRecipientDevice>> {
                    assertEquals(setOf(f.s.account, f.peer), accountIds)
                    assertEquals(listOf(f.s.account, f.peer).sorted(), order.takeLast(2))
                    reads.incrementAndGet()
                    return f.recipients.devices(spaceId, accountIds, at)
                }
            }
        val provider = f.provider()
        success(f.query(provider, f.command(provider, recipients, guarded)))
        assertTrue(reads.get() in 5..10)
        assertTrue(order.chunked(2).all { it == listOf(f.s.account, f.peer).sorted() })
        assertEquals(1, provider.queries.get())
        assertEquals(0, f.leases())
    }

    @Test
    fun malformedRequestCannotClaimCapacityAndUnknownRecipientsSkipProviderCalls() {
        val f = ready()
        val provider = f.provider()
        val bad = CollaborationMatrixKeyQuery(mapOf(f.s.user to listOf("*")))
        assertEquals("invalid_collaboration_key_query", failed(f.query(provider, request = bad)))
        val foreign =
            CollaborationMatrixKeyQuery(
                mapOf(f.s.user.replace("matrix.example.test", "foreign.test") to emptyList())
            )
        assertEquals(
            "invalid_collaboration_key_query",
            failed(f.query(provider, request = foreign)),
        )
        val unknown = f.user(UUID.randomUUID())
        assertTrue(
            success(
                    f.query(
                        provider,
                        request = CollaborationMatrixKeyQuery(mapOf(unknown to emptyList())),
                    )
                )
                .deviceKeys
                .getValue(unknown)
                .isEmpty()
        )
        assertEquals(0, provider.queries.get())
        assertEquals(0, f.leases())
    }

    @Test
    fun cancelledQueriesAndClaimsPropagateAndReleaseTheirExactCapacity() {
        val f = ready()
        val provider = f.provider()
        provider.onQuery = { throw CancellationException("fictional query cancellation") }
        assertThrows(CancellationException::class.java) { f.query(provider) }
        assertEquals(0, f.leases())
        provider.onQuery = {}
        provider.onClaim = { throw InterruptedException("fictional claim interruption") }
        assertThrows(InterruptedException::class.java) { f.claimKeys(provider) }
        assertEquals(0, f.leases())
    }

    @Test
    fun invalidSignedIdentityNeverConsumesOneTimeKeys() {
        val f = ready()
        val provider = f.provider()
        provider.materials[f.peerKeys.userId] =
            MatrixKeyFixture(f.peerKeys.userId, f.peerKeys.deviceId)
        assertEquals("collaboration_space_device_keys_changed", failed(f.claimKeys(provider)))
        assertEquals(0, provider.claims.get())
        assertEquals(0, f.leases())
    }

    @Test
    fun actualSynapseQueryAndSignedClaimRetainBothOriginalDevicesAcrossRestart() {
        MatrixProviderFixture("10m").use { provider ->
            provider.start()
            val f = SpaceKeyExchangeFixture(postgres)
            fun raw() = HttpMatrixClientDataSource(provider.client, provider.config, provider.clock)
            fun deadline() = Instant.now().plusSeconds(30)
            val group =
                InternalCollaborationSpaceMatrixRepository(
                    HmacMatrixJwtDataSource(provider.config),
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
            val providerKeys = InternalCollaborationMatrixKeyRepository(raw())
            for ((credential, keys) in listOf(own to f.ownKeys, peer to f.peerKeys)) success(
                providerKeys.upload(
                    CollaborationMatrixKeyUpload(
                        keys.device,
                        mapOf("signed_curve25519:once" to keys.signed()),
                        mapOf("signed_curve25519:backup" to keys.signed(true)),
                    ),
                    credential.accessToken,
                    deadline(),
                )
            )
            f.rotateProvider(own.accessToken, own.expiresAt)
            f.reserve()
            f.reserve(f.peer)
            val rooms =
                InternalCollaborationSpaceMatrixRoomRepository(raw(), provider.config.serverName)
            val request =
                CollaborationSpaceMatrixDirectRoom(
                    f.s.space,
                    f.conversation,
                    f.s.account,
                    f.peer,
                    own.userId,
                    peer.userId,
                )
            val room =
                success(rooms.create(request, own.accessToken, deadline()))
                    as CollaborationMatrixRoomCreation.Created
            success(rooms.join(room.roomId, peer.accessToken, deadline()))
            f.bind(room.roomId)
            val before = f.provenance()
            val queried = success(f.query(providerKeys))
            assertEquals(f.ownKeys.device, queried.deviceKeys[own.userId]?.get(own.deviceId))
            assertEquals(f.peerKeys.device, queried.deviceKeys[peer.userId]?.get(peer.deviceId))
            val claimed =
                success(f.claimKeys(providerKeys))
                    .oneTimeKeys
                    .getValue(peer.userId)
                    .getValue(peer.deviceId)
            assertEquals(1, claimed.size)
            assertNotEquals(true, claimed.values.single().fallback)
            provider.restart()
            val restarted = InternalCollaborationMatrixKeyRepository(raw())
            assertEquals(queried, success(f.query(restarted)))
            val fallback =
                success(f.claimKeys(restarted))
                    .oneTimeKeys
                    .getValue(peer.userId)
                    .getValue(peer.deviceId)
            assertEquals(true, fallback.values.single().fallback)
            assertEquals(before, f.provenance())
            assertEquals(0, f.leases())
        }
    }

    private fun ready() =
        SpaceKeyExchangeFixture(postgres).also {
            it.reserve()
            it.reserve(it.peer)
            it.bind()
        }

    private fun failed(result: Result<*>) = (result as Result.Failed).failure.code

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
