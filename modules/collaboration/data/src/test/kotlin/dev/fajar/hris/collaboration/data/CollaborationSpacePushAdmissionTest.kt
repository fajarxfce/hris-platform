package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.HandleCollaborationSpacePush
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/** Actual root RLS/queue transactions; no callback is allowed to acquire any transport token. */
@Testcontainers
class CollaborationSpacePushAdmissionTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun pendingCapabilityRetainsOwnedEventAndRetriesWithoutDuplicateRevision() {
        val f = Fixture()
        f.f.bind()
        val event = CollaborationPushEvent(f.f.boundRoom, "\$original-event")
        repeat(2) { assertTrue(f.accept(event).rejected.isEmpty()) }
        val first = requireNotNull(f.head())
        assertEquals(1L, first.revision)
        assertEquals(event, first.event)
        assertEquals(0L, f.binding.version)
        f.accept(null)
        assertEquals(first.revision, f.head()!!.revision)
        f.accept(event.copy(eventId = "\$second-event"))
        assertEquals(first.revision + 1, f.head()!!.revision)
        assertEquals(0, f.tokenReads)
    }

    @Test
    fun shortNativeGatewayAndProviderExpiryStillAllowOpaqueWakeButNotInteractiveAccess() {
        val f = Fixture()
        f.clock.offset = 1200
        val parent = success(f.f.s.tx { f.f.base.sessions.lock(f.f.s.parent, f.f.s.account) })!!
        val gateway =
            success(f.f.s.tx { f.f.s.repo.find(f.f.s.space, f.f.s.account, f.f.s.device) })!!
        val provider =
            success(f.f.s.tx { f.f.providers.metadata(f.f.s.space, f.f.s.account, f.f.s.device) })!!
        assertTrue(parent.accessExpiresAt < f.clock.instant())
        assertTrue(gateway.accessExpiresAt < f.clock.instant())
        assertTrue(provider.expiresAt < f.clock.instant())
        assertTrue(parent.expiresAt > f.clock.instant())
        assertTrue(gateway.expiresAt > f.clock.instant())
        assertTrue(f.accept(null).rejected.isEmpty())
        assertNotNull(f.head())
        assertInstanceOf(
            Result.Failed::class.java,
            f.f
                .command(f.f.provider(), clock = f.clock)
                .identity(f.f.tokens.accessToken, UUID.randomUUID()),
        )
        assertEquals(0, f.tokenReads)
    }

    @Test
    fun disabledRolloutPreservesKeysAndInvalidEntireBatchWritesNothing() {
        val f = Fixture()
        assertTrue(
            f.accept(null, f.command(policy = CollaborationSpacePolicy(false))).rejected.isEmpty()
        )
        assertNull(f.head())
        for (keys in
            listOf(
                emptyList(),
                listOf(f.key, f.key),
                listOf(f.key, CollaborationSpacePushKey("hriscp1_" + "a".repeat(64))),
                List(33) {
                    CollaborationSpacePushKey("hrisgp1_" + it.toString(16).padStart(64, '0'))
                },
            )) {
            assertEquals(
                "invalid_collaboration_push",
                failed(f.raw(CollaborationSpacePushHint(null, keys))),
            )
            assertNull(f.head())
        }
        val unknown = CollaborationSpacePushKey("hrisgp1_" + "e".repeat(64))
        assertEquals(
            listOf(unknown.value),
            success(f.raw(CollaborationSpacePushHint(null, listOf(unknown)))).rejected.map {
                it.value
            },
        )
        for (service in
            listOf(
                CollaborationServicePolicy(),
                CollaborationServicePolicy(true),
                CollaborationServicePolicy(false, true),
            )) assertEquals(
            "collaboration_push_unavailable",
            failed(f.raw(f.hint(), f.command(service = service))),
        )
    }

    @Test
    fun currentDirectoryLossAfterAppendRollsBackTheActualQueueWrite() {
        val f = Fixture()
        var reads = 0
        val spaces =
            object : CollaborationSpaceRepository by f.f.base.spaces {
                override fun current(): Result<UUID?> =
                    if (++reads >= 3) Result.Success(null) else f.f.base.spaces.current()
            }
        assertEquals(
            "collaboration_push_unavailable",
            failed(f.raw(f.hint(), f.command(spaces = spaces))),
        )
        assertEquals(3, reads)
        assertNull(f.head())
        assertTrue(f.accept(null).rejected.isEmpty())
        assertNotNull(f.head())
    }

    @Test
    fun finalDeadlineCancellationAndInterruptionRollBackAppendedHints() {
        val f = Fixture()
        var after: () -> Unit = {}
        val queue =
            object : CollaborationSpacePushQueueRepository by f.queue {
                override fun append(
                    binding: CollaborationSpacePushBinding,
                    event: CollaborationPushEvent?,
                    lifetimeSeconds: Int,
                ): Result<Boolean> =
                    f.queue.append(binding, event, lifetimeSeconds).also { after() }
            }
        val command = f.command(queue = queue)
        after = { f.clock.offset += 31 }
        assertEquals("collaboration_push_unavailable", failed(f.raw(f.hint(), command)))
        assertNull(f.head())
        f.clock.offset = 0
        after = { throw CancellationException("fictional cancellation") }
        assertThrows(CancellationException::class.java) { f.raw(f.hint(), command) }
        assertNull(f.head())
        after = { throw InterruptedException("fictional interruption") }
        assertThrows(InterruptedException::class.java) { f.raw(f.hint(), command) }
        assertNull(f.head())
        f.accept(null)
        assertNotNull(f.head())
    }

    @Test
    fun inactiveRegistrationAndStaleClientSuppressWithoutRetiringTheCapability() {
        val f = Fixture()
        val disabled =
            object : NativePushRegistrationRepository by f.metadataOnly {
                override fun find(accountId: UUID, sessionId: UUID) =
                    f.metadataOnly.find(accountId, sessionId).map { it?.copy(enabled = false) }
            }
        assertTrue(f.accept(null, f.command(nativePush = disabled)).rejected.isEmpty())
        assertNull(f.head())
        assertTrue(
            f.accept(null, f.command(policy = CollaborationSpacePolicy(true, 100, 100)))
                .rejected
                .isEmpty()
        )
        assertNull(f.head())
        f.accept(null)
        assertNotNull(f.head())
        assertEquals(0, f.tokenReads)
    }

    @Test
    fun foreignProjectionIsSuppressedWhileActualNativeRevocationRetiresOnlyItsOwnKey() {
        val f = Fixture()
        val foreign =
            object : NativeSessionRepository by f.f.base.sessions {
                override fun lock(sessionId: UUID, accountId: UUID) =
                    f.f.base.sessions.lock(sessionId, accountId).map {
                        it?.copy(accountId = f.f.peer)
                    }
            }
        assertTrue(f.accept(null, f.command(sessions = foreign)).rejected.isEmpty())
        assertNull(f.head())
        f.f.s.owner.update(
            "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
            f.f.s.parent,
        )
        assertEquals(listOf(f.key.value), f.accept(null).rejected.map { it.value })
        assertNull(f.head())
    }

    @Test
    fun peerOffboardingDoesNotEraseOwnedRoomWhileForeignEventsNeverWakeTheDevice() {
        val f = Fixture()
        f.f.bind()
        f.f.s.owner.update(
            "update company_memberships set active=false where account_id=?",
            f.f.peer,
        )
        val unknown = CollaborationPushEvent("!not-owned:matrix.example.test", "\$unowned")
        assertTrue(f.accept(unknown).rejected.isEmpty())
        assertNull(f.head())
        val event = CollaborationPushEvent(f.f.boundRoom, "\$owned-after-offboarding")
        assertTrue(f.accept(event).rejected.isEmpty())
        assertEquals(event, f.head()!!.event)
    }

    @Test
    fun failedLookupIsRetryableAndCannotBecomeUnknownOrFallbackRoomOwnership() {
        val f = Fixture()
        val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_lookup_failed"))
        val bindings =
            object : CollaborationSpacePushBindingRepository by f.f.pushBindings {
                override fun findKey(
                    key: CollaborationSpacePushKey
                ): Result<CollaborationSpacePushBinding?> = failure
            }
        assertEquals(
            "fixture_lookup_failed",
            failed(f.raw(f.hint(), f.command(bindings = bindings))),
        )
        val rooms =
            object : CollaborationSpaceRoomRepository by f.rooms {
                override fun boundRoom(
                    spaceId: UUID,
                    accountId: UUID,
                    serverName: String,
                    roomId: String,
                ): Result<CollaborationSpaceBoundRoom?> = failure
            }
        assertEquals(
            "fixture_lookup_failed",
            failed(
                f.raw(
                    f.hint(CollaborationPushEvent(f.f.boundRoom, "\$hint")),
                    f.command(rooms = rooms),
                )
            ),
        )
        assertNull(f.head())
    }

    @Test
    fun concurrentDuplicateCallbacksRetainOneRevisionUnderCurrentOwnerGuards() {
        val f = Fixture()
        f.f.bind()
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val calls =
                List(2) {
                    pool.submit<CollaborationSpacePushReceipt> {
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        f.accept(CollaborationPushEvent(f.f.boundRoom, "\$same-event"))
                    }
                }
            start.countDown()
            calls.forEach { assertTrue(it.get(15, TimeUnit.SECONDS).rejected.isEmpty()) }
            assertEquals(1L, f.head()!!.revision)
            assertEquals(0, f.tokenReads)
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private inner class Fixture {
        val f = SpaceKeyExchangeFixture(postgres)
        val clock = OffsetClock()
        var tokenReads = 0
        val queue =
            StoredCollaborationSpacePushQueueRepository(
                PostgresCollaborationSpacePushQueueDataSource(f.base.sql)
            )
        val rooms =
            StoredCollaborationSpaceRoomRepository(
                PostgresCollaborationSpaceRoomDataSource(f.base.sql)
            )
        val binding =
            CollaborationSpacePushBinding(
                f.s.space,
                f.s.account,
                f.s.device,
                f.s.parent,
                f.s.user,
                "HRISG_" + f.s.device.toString().replace("-", ""),
                0,
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                null,
            )
        val key: CollaborationSpacePushKey
        val metadataOnly =
            object : NativePushRegistrationRepository by f.nativePush {
                override fun token(
                    accountId: UUID,
                    sessionId: UUID,
                    expectedVersion: Long,
                ): Result<String?> {
                    tokenReads++
                    error("Callback must never acquire a native token")
                }
            }

        init {
            Flyway.configure().dataSource(f.s.source).target("137").load().migrate()
            f.s.owner.execute(
                "grant select,insert,update on collaboration_space_push_bindings,collaboration_space_push_hints to space_gateway_fixture"
            )
            success(f.s.tx { f.pushBindings.reserve(binding) })
            key = requireNotNull(success(f.s.tx { f.pushBindings.key(binding) }))
            val at = Instant.now().truncatedTo(ChronoUnit.MICROS)
            assertTrue(
                success(
                    f.s.tx {
                        f.nativePush.save(
                            NativePushRegistration(
                                f.s.parent,
                                f.s.account,
                                PushPlatform.ANDROID,
                                true,
                                0,
                                at,
                                at,
                                at.plusSeconds(2400),
                            ),
                            "fictional-unused-native-token",
                            null,
                        )
                    }
                )
            )
        }

        fun command(
            queue: CollaborationSpacePushQueueRepository = this.queue,
            bindings: CollaborationSpacePushBindingRepository = f.pushBindings,
            spaces: CollaborationSpaceRepository = f.base.spaces,
            sessions: NativeSessionRepository = f.base.sessions,
            nativePush: NativePushRegistrationRepository = metadataOnly,
            rooms: CollaborationSpaceRoomRepository = this.rooms,
            service: CollaborationServicePolicy = CollaborationServicePolicy(true, true),
            policy: CollaborationSpacePolicy = CollaborationSpacePolicy(true, 5, 5),
        ) =
            HandleCollaborationSpacePush(
                bindings,
                queue,
                f.s.repo,
                f.base.registry,
                f.base.providers,
                f.base.selections,
                f.base.legacy,
                f.base.completions,
                f.base.provisioning,
                spaces,
                rooms,
                StoredCollaborationSpaceConversationRepository(
                    PostgresCollaborationSpaceConversationDataSource(f.base.sql)
                ),
                sessions,
                nativePush,
                f.base.identities,
                f.s.transactions,
                service,
                policy,
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                clock,
                dev.fajar.hris.push.domain.repositories.PushRepository {
                    error("Callback cannot send to FCM")
                },
            )

        fun hint(event: CollaborationPushEvent? = null) =
            CollaborationSpacePushHint(event, listOf(key))

        fun raw(
            hint: CollaborationSpacePushHint,
            command: HandleCollaborationSpacePush = command(),
        ) = command.accept(hint, UUID.randomUUID(), clock.instant().plusSeconds(10))

        fun accept(
            event: CollaborationPushEvent?,
            command: HandleCollaborationSpacePush = command(),
        ) = success(raw(hint(event), command))

        fun head() = success(f.s.tx { queue.find(f.s.space, f.s.account, f.s.device) })
    }

    private class OffsetClock(var offset: Long = 0) : Clock() {
        override fun instant(): Instant = Instant.now().plusSeconds(offset)

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
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
