package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.AccessNativeCollaborationSpaceMatrixDevice
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.parseIdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceGatewayAccessTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun ownDefaultKeyReadPreservesAbsentEmptyAndPresentWithoutChangingPrivateProvenance() {
        val f = Fixture()
        val before = f.provenance()
        var calls = 0
        var outcome: CollaborationSecretStorageDefaultKey? = null
        val provider =
            object : CollaborationSecretStorageRepository {
                override fun defaultKey(
                    userId: String,
                    bearer: String,
                    deadline: Instant,
                ): Result<CollaborationSecretStorageDefaultKey?> {
                    calls++
                    assertEquals(f.identity.userId, userId)
                    assertEquals("fictional-provider-token", bearer)
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
                    assertEquals(1, f.leases())
                    assertTrue(deadline <= Instant.now().plusSeconds(30))
                    return Result.Success(outcome)
                }
            }
        val command = f.command(secretStorage = provider)
        val correlation = UUID.randomUUID()
        failure(
            command.secretStorageDefaultKey(
                f.tokens.accessToken,
                correlation,
                "@foreign:matrix.example.test",
            ),
            "collaboration_account_data_unavailable",
        )
        assertEquals(0, calls)
        failure(
            command.secretStorageDefaultKey(f.tokens.accessToken, correlation, f.identity.userId),
            "collaboration_account_data_not_found",
        )
        for (value in
            listOf(
                CollaborationSecretStorageDefaultKey(null),
                CollaborationSecretStorageDefaultKey("existing-key"),
            )) {
            outcome = value
            assertEquals(
                value,
                success(
                    command.secretStorageDefaultKey(
                        f.tokens.accessToken,
                        correlation,
                        f.identity.userId,
                    )
                ),
            )
        }
        assertEquals(3, calls)
        assertEquals(0, f.calls.get())
        assertEquals(0, f.leases())
        assertEquals(before, f.provenance())
    }

    @Test
    fun defaultKeyReadRechecksCurrentDenialOnBothProviderOutcomes() {
        val f = Fixture()
        var calls = 0
        for (succeeds in listOf(false, true)) {
            var denied = false
            val spaces =
                object : CollaborationSpaceRepository by f.base.spaces {
                    override fun current(): Result<UUID?> =
                        if (denied) Result.Success(null) else f.base.spaces.current()
                }
            val provider =
                object : CollaborationSecretStorageRepository {
                    override fun defaultKey(
                        userId: String,
                        bearer: String,
                        deadline: Instant,
                    ): Result<CollaborationSecretStorageDefaultKey?> {
                        calls++
                        denied = true
                        return if (succeeds)
                            Result.Success(CollaborationSecretStorageDefaultKey("withheld"))
                        else
                            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_down"))
                    }
                }
            failure(
                f.command(spaces = spaces, secretStorage = provider)
                    .secretStorageDefaultKey(
                        f.tokens.accessToken,
                        UUID.randomUUID(),
                        f.identity.userId,
                    ),
                "collaboration_access_denied",
            )
            assertEquals(0, f.leases())
        }
        assertEquals(2, calls)
    }

    @Test
    fun cancelledOrLateDefaultKeyReadsReleaseTheExactRequestAndNeverReplaceKeys() {
        val f = Fixture()
        val before = f.provenance()
        for (cancel in listOf(true, false)) {
            val clock = OffsetClock()
            val provider =
                object : CollaborationSecretStorageRepository {
                    override fun defaultKey(
                        userId: String,
                        bearer: String,
                        deadline: Instant,
                    ): Result<CollaborationSecretStorageDefaultKey?> {
                        if (cancel) throw CancellationException("fixture cancellation")
                        clock.seconds = 30
                        return Result.Success(CollaborationSecretStorageDefaultKey("late-key"))
                    }
                }
            val command = f.command(clock = clock, secretStorage = provider)
            val read = {
                command.secretStorageDefaultKey(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    f.identity.userId,
                )
            }
            if (cancel) assertThrows(CancellationException::class.java) { read() }
            else failure(read(), "collaboration_admission_changed")
            assertEquals(0, f.leases())
            assertEquals(before, f.provenance())
        }
    }

    @Test
    fun exactGroupIdentityUsesOneOwnedRequestOutsideSqlAndCapabilitiesNeverDecrypt() {
        val f = Fixture()
        val snapshot = f.provenance()
        assertEquals(f.identity, success(f.access()))
        assertEquals(1, f.calls.get())
        assertEquals(0, f.leases())
        val metadata =
            object : CollaborationSpaceProviderSessionRepository by f.providers {
                override fun credential(
                    expected: CollaborationSpaceProviderState
                ): Result<CollaborationSpaceProviderSession> =
                    error("Capabilities must not decrypt a provider token")
            }
        assertEquals(
            Result.Success(Unit),
            f.command(providers = metadata).capabilities(f.tokens.accessToken, UUID.randomUUID()),
        )
        assertEquals(1, f.calls.get())
        assertEquals(0, f.leases())
        assertEquals(snapshot, f.provenance())
    }

    @Test
    fun malformedOldNamespaceAndExpiredAccessCannotStartProviderWork() {
        val f = Fixture()
        for (token in
            listOf(
                "",
                "Bearer ${f.tokens.accessToken}",
                f.tokens.refreshToken,
                "hris_mx_a_" + "x".repeat(43),
                "hris_mxg_a_" + "x".repeat(43),
                f.tokens.accessToken + " ",
            )) failure(f.access(token), "collaboration_space_gateway_authentication_required")
        failure(
            f.access(
                command = f.command(clock = Clock.fixed(f.tokens.accessExpiresAt, ZoneOffset.UTC))
            ),
            "collaboration_space_gateway_authentication_required",
        )
        failure(
            f.access(command = f.command(policy = CollaborationSpacePolicy(true, 6, 6))),
            "client_update_required",
        )
        failure(
            f.access(command = f.command(policy = CollaborationSpacePolicy(false, 5, 5))),
            "collaboration_unavailable",
        )
        assertEquals(0, f.calls.get())
        assertEquals(0, f.leases())
    }

    @Test
    fun currentDenialTakesPrecedenceOnBothProviderOutcomesAndCleanupSurvivesNativePurge() {
        val f = Fixture()
        for (succeeds in listOf(false, true)) {
            var denied = false
            val spaces =
                object : CollaborationSpaceRepository by f.base.spaces {
                    override fun current(): Result<UUID?> =
                        if (denied) Result.Success(null) else f.base.spaces.current()
                }
            val matrix =
                f.matrix {
                    denied = true
                    if (succeeds) Result.Success(f.identity)
                    else Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_down"))
                }
            failure(
                f.access(command = f.command(matrix = matrix, spaces = spaces)),
                "collaboration_access_denied",
            )
            assertEquals(0, f.leases())
        }
        val matrix =
            f.matrix {
                f.s.owner.update("delete from native_sessions where id=?", f.s.parent)
                Result.Success(f.identity)
            }
        failure(f.access(command = f.command(matrix = matrix)), "native_session_invalid")
        assertEquals(0, f.leases())
        assertEquals(3, f.calls.get())
        assertEquals(
            1,
            f.s.owner.queryForObject(
                "select count(*) from collaboration_space_devices",
                Int::class.java,
            ),
        )
    }

    @Test
    fun providerAndNativeGenerationChangesWithholdResponsesButANewAdmittedRequestCanRecover() {
        val f = Fixture()
        val provider =
            f.matrix {
                f.rotateProvider()
                Result.Success(f.identity)
            }
        failure(f.access(command = f.command(matrix = provider)), "collaboration_admission_changed")
        assertEquals(0, f.leases())
        assertEquals(f.identity, success(f.access()))
        val native =
            f.matrix {
                f.s.owner.update(
                    "update native_sessions set version=version+1 where id=?",
                    f.s.parent,
                )
                Result.Success(f.identity)
            }
        failure(f.access(command = f.command(matrix = native)), "collaboration_admission_changed")
        assertEquals(0, f.leases())
        assertEquals(f.identity, success(f.access()))
    }

    @Test
    fun malformedLateCancelledAndFailedProviderResultsReleaseTheirExactRequest() {
        val f = Fixture()
        for (mode in listOf("user", "device", "guest", "late", "failed", "cancel", "interrupt")) {
            val clock = OffsetClock()
            val matrix =
                f.matrix {
                    when (mode) {
                        "late" -> {
                            clock.seconds = 30
                            Result.Success(f.identity)
                        }
                        "failed" ->
                            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_down"))
                        "cancel" -> throw CancellationException("fixture cancellation")
                        "interrupt" -> throw InterruptedException("fixture interruption")
                        else ->
                            Result.Success(
                                f.identity.copy(
                                    userId =
                                        if (mode == "user") "@foreign:matrix.example.test"
                                        else f.identity.userId,
                                    deviceId =
                                        if (mode == "device") "OTHER" else f.identity.deviceId,
                                    isGuest = mode == "guest",
                                )
                            )
                    }
                }
            val run = { f.access(command = f.command(matrix = matrix, clock = clock)) }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { run() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { run() }
                "late" -> failure(run(), "collaboration_admission_changed")
                "failed" -> failure(run(), "fixture_provider_down")
                else -> failure(run(), "collaboration_provider_invalid_response")
            }
            assertEquals(0, f.leases(), mode)
        }
    }

    @Test
    fun sevenConcurrentRequestsLeaveRenewalCapacityAndLateReleaseCannotDeleteASuccessor() {
        val f = Fixture()
        val entered = CountDownLatch(7)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(7)
        try {
            val matrix =
                f.matrix {
                    entered.countDown()
                    assertTrue(release.await(15, TimeUnit.SECONDS))
                    Result.Success(f.identity)
                }
            val futures =
                (1..7).map {
                    pool.submit<Result<CollaborationMatrixIdentity>> {
                        f.access(command = f.command(matrix = matrix))
                    }
                }
            assertTrue(entered.await(15, TimeUnit.SECONDS))
            failure(f.access(), "collaboration_space_gateway_busy")
            assertEquals(7, f.calls.get())
            val renewal =
                requireNotNull(
                    success(
                        f.s.tx {
                            f.requests.claim(
                                CollaborationSpaceRequestPosition(
                                    f.owner,
                                    CollaborationSpaceRequestKind.PROVIDER_RENEWAL,
                                ),
                                Instant.now().plusSeconds(15).truncatedTo(ChronoUnit.MICROS),
                            )
                        }
                    )
                )
            assertEquals(0, renewal.slot)
            assertTrue(success(f.s.tx { f.requests.release(renewal) }))
            release.countDown()
            for (future in futures) assertEquals(
                f.identity,
                success(future.get(20, TimeUnit.SECONDS)),
            )
            assertEquals(0, f.leases())
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
        var successor: CollaborationSpaceRequestLease? = null
        val matrix =
            f.matrix {
                success(
                    f.s.tx {
                        f.requests.find(f.owner, 1).flatMap { old ->
                            val original = requireNotNull(old)
                            f.requests
                                .release(original)
                                .flatMap {
                                    f.requests.claim(
                                        original.position,
                                        Instant.now().plusSeconds(20).truncatedTo(ChronoUnit.MICROS),
                                    )
                                }
                                .map { successor = requireNotNull(it) }
                        }
                    }
                )
                Result.Success(f.identity)
            }
        failure(f.access(command = f.command(matrix = matrix)), "collaboration_space_request_lost")
        assertEquals(successor, success(f.s.tx { f.requests.find(f.owner, 1) }))
        assertTrue(success(f.s.tx { f.requests.release(requireNotNull(successor)) }))
    }

    @Test
    fun admittedGroupIdentityMatchesActualProviderWithoutCreatingACompanyRuntime() {
        MatrixProviderFixture("10m").use { provider ->
            provider.start()
            val f = Fixture()
            val group =
                InternalCollaborationSpaceMatrixRepository(
                    dev.fajar.hris.collaboration.data.matrix.HmacMatrixJwtDataSource(
                        provider.config
                    ),
                    dev.fajar.hris.collaboration.data.matrix.HttpMatrixClientDataSource(
                        provider.client,
                        provider.config,
                        Clock.systemUTC(),
                    ),
                    Clock.systemUTC(),
                )
            val at = Instant.now()
            val candidate =
                success(
                    group.login(
                        CollaborationSpaceMatrixLogin(
                            f.s.space,
                            f.s.account,
                            f.s.device,
                            null,
                            null,
                            at,
                            at.plusSeconds(30),
                        )
                    )
                )
            f.rotateProvider(candidate.accessToken, candidate.expiresAt)
            assertEquals(
                f.identity,
                success(f.access(command = f.command(matrix = provider.repository))),
            )
            provider.restart()
            assertEquals(
                f.identity,
                success(f.access(command = f.command(matrix = provider.repository))),
            )
            assertEquals(0, f.leases())
            assertEquals(
                0,
                f.s.owner.queryForObject(
                    "select count(*) from collaboration_legacy_runtime",
                    Int::class.java,
                ),
            )
        }
    }

    private inner class Fixture {
        val base = SpaceGatewayBootstrapFixture(postgres)
        val s = base.storage
        val owner = CollaborationSpaceRequestOwner(s.space, s.account, s.device, s.parent)
        val requests =
            StoredCollaborationSpaceRequestRepository(
                PostgresCollaborationSpaceRequestDataSource(base.sql)
            )
        val providers =
            EncryptedCollaborationSpaceProviderSessionRepository(
                PostgresCollaborationSpaceProviderSessionDataSource(base.sql),
                JceIdentityTokenDataSource(
                    parseIdentityKeyring(
                        "fixture",
                        "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 63 }),
                    )
                ),
            )
        val calls = AtomicInteger()
        val identity =
            CollaborationMatrixIdentity(
                s.user,
                "HRISG_" + s.device.toString().replace("-", ""),
                false,
            )
        val tokens: CollaborationSpaceGatewayTokens

        init {
            Flyway.configure().dataSource(s.source).target("127").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on collaboration_space_request_leases to space_gateway_fixture,space_gateway_worker"
            )
            rotateProvider()
            base.clock.at = Instant.now()
            tokens = success(base.bootstrap(command = base.command(providers = providers)))
        }

        fun rotateProvider(
            token: String = "fictional-provider-token",
            expiresAt: Instant = Instant.now().plusSeconds(300),
        ) {
            success(
                s.tx {
                    providers.metadata(s.space, s.account, s.device).flatMap {
                        val before = requireNotNull(it)
                        providers.rotate(
                            CollaborationSpaceProviderSession(
                                before.copy(
                                    generation = before.generation + 1,
                                    updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
                                    expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS),
                                ),
                                token,
                            ),
                            before.generation,
                        )
                    }
                }
            )
        }

        fun matrix(
            action: () -> Result<CollaborationMatrixIdentity> = { Result.Success(identity) }
        ) =
            object : CollaborationMatrixRepository {
                override fun login(
                    request: CollaborationMatrixLogin
                ): Result<CollaborationMatrixCredential> =
                    error("Group access must not use company login")

                override fun logoutDevice(accessToken: String): Result<Unit> =
                    error("Group access must not logout")

                override fun whoAmI(
                    accessToken: String,
                    deadline: Instant,
                ): Result<CollaborationMatrixIdentity> {
                    calls.incrementAndGet()
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
                    assertEquals("fictional-provider-token", accessToken)
                    assertTrue(leases() in 1..7)
                    assertTrue(deadline <= Instant.now().plusSeconds(30))
                    return action()
                }
            }

        fun command(
            matrix: CollaborationMatrixRepository = matrix(),
            providers: CollaborationSpaceProviderSessionRepository = this.providers,
            spaces: CollaborationSpaceRepository = base.spaces,
            policy: CollaborationSpacePolicy = CollaborationSpacePolicy(true, 5, 5),
            clock: Clock = Clock.systemUTC(),
            secretStorage: CollaborationSecretStorageRepository =
                DisabledCollaborationSecretStorageRepository(),
        ) =
            AccessNativeCollaborationSpaceMatrixDevice(
                s.repo,
                requests,
                base.registry,
                providers,
                matrix,
                base.selections,
                base.legacy,
                base.completions,
                base.provisioning,
                spaces,
                base.sessions,
                base.identities,
                s.transactions,
                IdentitySecurityPolicy(),
                policy,
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                clock,
                StoredCollaborationSpaceKeyRepository(
                    PostgresCollaborationSpaceKeyDataSource(base.sql)
                ),
                DisabledCollaborationMatrixKeyRepository(),
                base.journal,
                StoredCollaborationSpaceRecipientRepository(
                    PostgresCollaborationSpaceRecipientDataSource(base.sql)
                ),
                DisabledCollaborationMatrixToDeviceRepository(),
                StoredCollaborationSpaceRoomRepository(
                    PostgresCollaborationSpaceRoomDataSource(base.sql)
                ),
                DisabledCollaborationMatrixRoomMessageRepository(),
                DisabledCollaborationSpaceMatrixRoomHistoryRepository(),
                StoredCollaborationSpaceConversationRepository(
                    PostgresCollaborationSpaceConversationDataSource(base.sql)
                ),
                EncryptedCollaborationSpaceSyncCursorRepository(collaborationCursorFixture()),
                DisabledCollaborationSpaceMatrixSyncRepository(),
                DisabledCollaborationSpaceMatrixRoomAccessRepository(),
                CollaborationServicePolicy(),
                EncryptedCollaborationSpacePushBindingRepository(
                    PostgresCollaborationSpacePushBindingDataSource(base.sql),
                    JceIdentityTokenDataSource(s.currentKeys),
                ),
                DisabledCollaborationMatrixPusherRepository(),
                dev.fajar.hris.identity.data.repositories.StoredNativePushRegistrationRepository(
                    dev.fajar.hris.identity.data.datasources
                        .PostgresNativePushRegistrationDataSource(base.sql),
                    JceIdentityTokenDataSource(s.currentKeys),
                ),
                secretStorage = secretStorage,
            )

        fun access(
            token: String = tokens.accessToken,
            command: AccessNativeCollaborationSpaceMatrixDevice = command(),
        ) = command.identity(token, UUID.randomUUID())

        fun leases() =
            s.owner.queryForObject(
                "select count(*) from collaboration_space_request_leases",
                Int::class.java,
            )!!

        fun provenance() =
            listOf(
                    "collaboration_space_devices",
                    "collaboration_space_provider_sessions",
                    "collaboration_space_gateway_sessions",
                    "collaboration_legacy_runtime",
                )
                .associateWith { s.owner.queryForList("select * from $it") }
    }

    private class OffsetClock(var seconds: Long = 0) : Clock() {
        override fun instant(): Instant = Instant.now().plusSeconds(seconds)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result) {
            (result as? Result.Failed)?.failure?.code ?: "unexpected result"
        }
        return (result as Result.Success).value
    }

    private fun failure(result: Result<*>, code: String) {
        assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(code, (result as Result.Failed).failure.code)
    }
}
