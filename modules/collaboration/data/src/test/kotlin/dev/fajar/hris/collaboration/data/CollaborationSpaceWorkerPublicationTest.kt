package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.core.database.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.parseIdentityKeyring
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper

/** Real RLS, guards, sessions, receipts and journal; hooks inject failures after actual writes. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollaborationSpaceWorkerPublicationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    private lateinit var owner: JdbcTemplate
    private lateinit var runtime: DriverManagerDataSource
    private lateinit var space: UUID
    private lateinit var workerQueue: CollaborationSpaceProvisioningRepository
    private val fixtures = mutableListOf<Fixture>()
    private lateinit var now: Instant
    private val server = "matrix.example.test"
    private val client = ClientVersion(ClientPlatform.ANDROID, 28)

    @BeforeAll
    fun database() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).load().migrate()
        owner = JdbcTemplate(source)
        owner.execute(
            "create role space_advance_command_fixture login password 'isolated-fixture-only'"
        )
        owner.execute("grant usage on schema public to space_advance_command_fixture")
        owner.execute(
            "grant select on all tables in schema public to space_advance_command_fixture"
        )
        owner.execute(
            "grant update on accounts,native_sessions,collaboration_space_devices,collaboration_space_provisioning,collaboration_space_provider_sessions to space_advance_command_fixture"
        )
        owner.execute(
            "grant insert on collaboration_space_provider_sessions,collaboration_space_provisioning,collaboration_space_identities,collaboration_space_devices,collaboration_space_device_sessions,operation_receipts,audit_entries,platform_audit_metadata,outbox_events to space_advance_command_fixture"
        )
        owner.execute("grant hris_worker_capability to space_advance_command_fixture")
        runtime =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "space_advance_command_fixture",
                "isolated-fixture-only",
            )
        owner.execute("create role space_advance_worker login password 'isolated-fixture-only'")
        owner.execute("grant usage on schema public to space_advance_worker")
        owner.execute("grant hris_worker_capability to space_advance_worker")
        owner.execute(
            "grant select,update on collaboration_space_provisioning to space_advance_worker"
        )
        workerQueue =
            StoredCollaborationSpaceProvisioningRepository(
                PostgresCollaborationSpaceProvisioningDataSource(
                    DSL.using(
                        DriverManagerDataSource(
                            postgres.jdbcUrl,
                            "space_advance_worker",
                            "isolated-fixture-only",
                        ),
                        SQLDialect.POSTGRES,
                    )
                )
            )
        now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        space =
            requireNotNull(
                owner.queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
    }

    @BeforeEach
    fun clockForTest() {
        now = Instant.now().truncatedTo(ChronoUnit.MICROS)
    }

    @AfterEach
    fun endFixtureWork() {
        for (f in fixtures) {
            val rows =
                owner.queryForList(
                    "select device_id,revision from collaboration_space_provisioning where account_id=?",
                    f.account,
                )
            for (row in rows) success(
                f.transactions.run(f.actor) {
                    f.provisioning.cancel(
                        space,
                        f.account,
                        row["device_id"] as UUID,
                        (row["revision"] as Number).toLong(),
                    )
                }
            )
        }
        fixtures.clear()
    }

    @Test
    fun nativeRegistrationPublishesOneEncryptedCredentialWithoutHoldingSqlDuringProviderIo() {
        for (retainedUser in listOf(false, true)) {
            val f = Fixture(verifiedMfa = true)
            if (retainedUser) f.activeLegacy()
            val originalLegacy =
                owner.queryForList(
                    "select * from collaboration_provider_sessions where account_id=?",
                    f.account,
                )
            val id = f.register()
            val device = f.device(id)
            val lease = f.claim(id)
            val matrix = f.provider(id)
            success(f.advance(lease, matrix))
            assertEquals(1, matrix.calls.get())
            assertEquals(CollaborationDeviceState.ACTIVE, f.device(id).state)
            assertEquals(1, f.device(id).version)
            val credential = f.credential(id)
            assertEquals(device.matrixUserId, credential.state.matrixUserId)
            assertEquals(device.matrixDeviceId, credential.state.matrixDeviceId)
            assertEquals(f.session, credential.state.nativeSessionId)
            assertEquals(0, credential.state.generation)
            assertTrue(credential.state.expiresAt > Instant.now())
            assertEquals(CollaborationSpaceProvisioningState.SUCCEEDED, f.work(id).state)
            f.activatedCount(1)
            assertInstanceOf(Result.Failed::class.java, f.advance(lease, matrix))
            assertEquals(1, matrix.calls.get())
            assertEquals(
                originalLegacy,
                owner.queryForList(
                    "select * from collaboration_provider_sessions where account_id=?",
                    f.account,
                ),
            )
        }
    }

    @Test
    fun forgedProofStaleLeaseAndRetainedDeviceHaveNoProviderSideEffects() {
        val f = Fixture(verifiedMfa = true)
        val id = f.register()
        val lease = f.claim(id)
        val head = f.work(id)
        val matrix = f.provider(id)
        for (changed in
            listOf(
                lease.copy(token = UUID.randomUUID()),
                lease.copy(ownerId = UUID.randomUUID()),
                lease.copy(attempt = 2),
                lease.copy(until = lease.until.minusNanos(1000)),
                lease.copy(request = lease.request.copy(nativeSessionVersion = 1)),
                lease.copy(request = lease.request.copy(credentialVersion = 1)),
                lease.copy(
                    request =
                        lease.request.copy(
                            authenticatedAt = lease.request.authenticatedAt.minusNanos(1000)
                        )
                ),
                lease.copy(request = lease.request.copy(mfaVerifiedAt = null)),
                lease.copy(request = lease.request.copy(client = client.copy(build = 27))),
            )) assertInstanceOf(Result.Failed::class.java, f.advance(changed, matrix))
        assertEquals(0, matrix.calls.get())
        assertEquals(head, f.work(id))
        assertNull(f.state(id))
        f.activatedCount(0)

        val retained = Fixture()
        val original = retained.activeLegacy()
        val retainedId = retained.register(original)
        val retainedMatrix = retained.provider(retainedId)
        failure(
            retained.advance(retained.claim(retainedId), retainedMatrix),
            "collaboration_device_migration_required",
        )
        assertEquals(0, retainedMatrix.calls.get())
        assertNull(retained.state(retainedId))
        assertEquals(CollaborationDeviceState.PROVISIONING, retained.device(retainedId).state)
    }

    @Test
    fun lateNativeGroupLeaseGenerationDeviceAndClockChangesRejectTheCandidate() {
        for (change in
            listOf("native", "offboard", "cancel", "generation", "device", "expiry", "backward")) {
            val f = Fixture()
            val id = f.register()
            val lease = f.claim(id)
            val clock = MutableClock(Instant.now())
            var expectedDevice = f.device(id)
            var expectedHead = f.work(id)
            var expectedState: CollaborationSpaceProviderState? = null
            val matrix =
                f.provider(id) {
                    when (change) {
                        "native" ->
                            owner.update(
                                "update native_sessions set version=version+1,rotated_at=clock_timestamp() where id=?",
                                f.session,
                            )
                        "offboard" ->
                            owner.update(
                                "update company_memberships set active=false,version=version+1 where account_id=?",
                                f.account,
                            )
                        "cancel" -> {
                            assertTrue(f.cancel(id))
                            expectedHead = f.work(id)
                        }
                        "generation" -> {
                            val at = Instant.now().truncatedTo(ChronoUnit.MICROS)
                            val state =
                                CollaborationSpaceProviderState(
                                    space,
                                    f.account,
                                    id,
                                    f.session,
                                    expectedDevice.matrixUserId,
                                    expectedDevice.matrixDeviceId,
                                    0,
                                    at,
                                    at,
                                    at.plusSeconds(300),
                                )
                            success(
                                f.transactions.run(f.actor) {
                                    f.credentials.create(
                                        CollaborationSpaceProviderSession(
                                            state,
                                            "fixture-competing-token",
                                        )
                                    )
                                }
                            )
                            expectedState = f.state(id)
                        }
                        "device" -> {
                            val at = Instant.now().truncatedTo(ChronoUnit.MICROS)
                            success(
                                f.transactions.run(f.actor) {
                                    f.registry.replace(
                                        expectedDevice.copy(
                                            state = CollaborationDeviceState.REVOKED,
                                            version = 1,
                                            updatedAt = at,
                                            revokedAt = at,
                                        ),
                                        0,
                                    )
                                }
                            )
                            expectedDevice = f.device(id)
                        }
                        "expiry" -> clock.at = lease.until
                        "backward" -> clock.at = clock.at.minusSeconds(1)
                    }
                }
            assertInstanceOf(Result.Failed::class.java, f.advance(lease, matrix, clock = clock))
            assertEquals(1, matrix.calls.get())
            assertEquals(expectedHead, f.work(id))
            assertEquals(expectedDevice, f.device(id))
            assertEquals(expectedState, f.state(id))
            f.activatedCount(0)
        }
    }

    @Test
    fun failuresCancellationAndInterruptionRollBackEveryPublicationWrite() {
        for (stage in listOf("credential", "device", "queue", "journal")) for (mode in
            listOf("failure", "cancel", "interrupt")) {
            val f = Fixture()
            val id = f.register()
            val device = f.device(id)
            val lease = f.claim(id)
            val head = f.work(id)
            val matrix = f.provider(id)
            fun injected(): Result<Unit> {
                if (mode == "cancel") throw CancellationException("fixture cancellation")
                if (mode == "interrupt") throw InterruptedException("fixture interruption")
                return Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_write_failed"))
            }
            val credentials =
                object : CollaborationSpaceProviderSessionRepository by f.credentials {
                    override fun create(session: CollaborationSpaceProviderSession): Result<Unit> =
                        f.credentials.create(session).flatMap {
                            if (stage == "credential") injected() else Result.Success(Unit)
                        }
                }
            val registry =
                object : CollaborationSpaceDeviceRepository by f.registry {
                    override fun replace(
                        device: CollaborationSpaceDevice,
                        expectedVersion: Long,
                    ): Result<Unit> =
                        f.registry.replace(device, expectedVersion).flatMap {
                            if (stage == "device") injected() else Result.Success(Unit)
                        }
                }
            val queue =
                object : CollaborationSpaceProvisioningRepository by f.provisioning {
                    override fun finish(
                        lease: CollaborationSpaceProvisioningLease,
                        failure: CollaborationSpaceProvisioningFailure?,
                    ): Result<Boolean> =
                        f.provisioning.finish(lease, failure).flatMap { value ->
                            if (stage == "queue") injected().map { value }
                            else Result.Success(value)
                        }
                }
            val journal = hookJournal { actor, record ->
                f.journal.record(actor, record).flatMap {
                    if (stage == "journal") injected() else Result.Success(Unit)
                }
            }
            fun run() = f.advance(lease, matrix, registry, queue, credentials, journal)
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { run() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { run() }
                else -> failure(run(), "fixture_write_failed")
            }
            assertEquals(device, f.device(id))
            assertEquals(head, f.work(id))
            assertNull(f.state(id))
            f.activatedCount(0)
            success(f.advance(lease, matrix))
            f.activatedCount(1)
            assertEquals(2, matrix.calls.get())
        }
    }

    @Test
    fun validCredentialIsReusedAndExpiredGenerationRotatesWithoutLosingProvenance() {
        for (expired in listOf(false, true)) {
            val f = Fixture()
            val id = if (expired) f.seed().request.deviceId else f.register()
            val device = f.device(id)
            val at =
                if (expired) f.baseAt.plusSeconds(10)
                else Instant.now().truncatedTo(ChronoUnit.MICROS)
            val state =
                CollaborationSpaceProviderState(
                    space,
                    f.account,
                    id,
                    f.session,
                    device.matrixUserId,
                    device.matrixDeviceId,
                    0,
                    at,
                    at,
                    at.plusSeconds(if (expired) 10 else 300),
                )
            success(
                f.transactions.run(f.actor) {
                    f.credentials.create(
                        CollaborationSpaceProviderSession(state, "fixture-existing-token")
                    )
                }
            )
            val lease = f.claim(id)
            val matrix = f.provider(id)
            success(f.advance(lease, matrix))
            val stored = f.credential(id)
            assertEquals(state.createdAt, stored.state.createdAt)
            assertEquals(if (expired) 1L else 0L, stored.state.generation)
            assertEquals(if (expired) 1 else 0, matrix.calls.get())
            if (expired) assertNotEquals("fixture-existing-token", stored.accessToken)
            else assertEquals("fixture-existing-token", stored.accessToken)
            f.activatedCount(1)
        }
    }

    @Test
    fun competingCandidatesPublishExactlyOneWinnerWithoutReplacingItsCredential() {
        val f = Fixture()
        val id = f.register()
        val device = f.device(id)
        val lease = f.claim(id)
        val barrier = CyclicBarrier(2)
        val tokens = ConcurrentHashMap.newKeySet<String>()
        val matrix = MatrixStub {
            val token = "fictional-token-${UUID.randomUUID()}"
            tokens.add(token)
            barrier.await(5, TimeUnit.SECONDS)
            Result.Success(
                CollaborationMatrixCredential(
                    device.matrixUserId,
                    device.matrixDeviceId,
                    token,
                    Instant.now().plusSeconds(590),
                )
            )
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                (1..2)
                    .map { pool.submit<Result<Unit>> { f.advance(lease, matrix) } }
                    .map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(1, results.count { it is Result.Failed })
            assertEquals(2, matrix.calls.get())
            assertTrue(f.credential(id).accessToken in tokens)
            assertEquals(0, f.credential(id).state.generation)
            assertEquals(CollaborationSpaceProvisioningState.SUCCEEDED, f.work(id).state)
            assertEquals(1, f.device(id).version)
            f.activatedCount(1)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun invalidProviderCandidatesAndLateCompletionCannotCommitPartialActivation() {
        for (change in listOf("user", "device", "token", "expiry")) {
            val f = Fixture()
            val id = f.register()
            val device = f.device(id)
            val lease = f.claim(id)
            val head = f.work(id)
            val matrix = MatrixStub {
                Result.Success(
                    CollaborationMatrixCredential(
                        if (change == "user") "@foreign:matrix.example.test"
                        else device.matrixUserId,
                        if (change == "device") "HRISG_foreign" else device.matrixDeviceId,
                        if (change == "token") "invalid token" else "fixture-token",
                        Instant.now().plusSeconds(if (change == "expiry") 700 else 590),
                    )
                )
            }
            failure(f.advance(lease, matrix), "collaboration_provider_invalid_response")
            assertEquals(device, f.device(id))
            assertEquals(head, f.work(id))
            assertNull(f.state(id))
            f.activatedCount(0)
            assertTrue(f.cancel(id))
        }
        for (change in listOf("expiry", "backward", "mfa", "interrupt")) {
            val f = Fixture()
            val id = f.register()
            val device = f.device(id)
            val lease = f.claim(id)
            val head = f.work(id)
            val at = Instant.now()
            val clock = MutableClock(at)
            val matrix = f.provider(id)
            val journal = hookJournal { actor, record ->
                f.journal.record(actor, record).map {
                    assertEquals(
                        CollaborationSpaceProvisioningState.SUCCEEDED,
                        success(f.provisioning.metadata(space, f.account, id))?.state,
                    )
                    when (change) {
                        "expiry" -> clock.at = lease.until
                        "backward" -> clock.at = at.minusSeconds(1)
                        "mfa" ->
                            owner.update(
                                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'identity.manage')",
                                f.company,
                                f.account,
                            )
                        "interrupt" -> Thread.currentThread().interrupt()
                    }
                    Unit
                }
            }
            if (change == "interrupt") {
                try {
                    assertThrows(InterruptedException::class.java) {
                        f.advance(lease, matrix, journal = journal, clock = clock)
                    }
                    assertTrue(Thread.currentThread().isInterrupted)
                } finally {
                    Thread.interrupted()
                }
            } else
                assertInstanceOf(
                    Result.Failed::class.java,
                    f.advance(lease, matrix, journal = journal, clock = clock),
                )
            assertEquals(device, f.device(id))
            assertEquals(head, f.work(id))
            assertNull(f.state(id))
            f.activatedCount(0)
        }
    }

    @Test
    fun failureSettlementDefersBoundedWorkAndRetainsOneAuditWithoutRawFailureData() {
        val f = Fixture()
        val id = f.register()
        val lease = f.claim(id)
        val device = f.device(id)
        val failure =
            Failure(
                FailureKind.UNAVAILABLE,
                "collaboration_provider_unavailable",
                parameters =
                    mapOf("retryAfterSeconds" to "99999999", "secret" to "fixture-not-for-journal"),
            )
        assertTrue(success(f.settle(lease, failure)))
        val deferred = f.work(id)
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, deferred.state)
        assertEquals(1, deferred.attempts)
        assertNull(deferred.lease)
        assertNull(deferred.lastOutcome)
        assertEquals(device, f.device(id))
        assertNull(f.state(id))
        assertFalse(success(f.settle(lease, failure)))
        val rows =
            owner.queryForList(
                "select details::text as details from audit_entries where actor_id=? and action='collaboration.space_provisioning_deferred'",
                f.account,
            )
        assertEquals(1, rows.size)
        assertFalse(rows.single()["details"].toString().contains("fixture-not-for-journal"))
        assertFalse(rows.single()["details"].toString().contains("99999999"))
        assertFalse(rows.single()["details"].toString().contains(lease.token.toString()))
        val millis =
            java.time.Duration.between(Instant.now(), deferred.availableAt)
                .toMillis()
                .coerceAtLeast(0) + 100
        assertTrue(millis <= 3100)
        Thread.sleep(millis)
        val next = f.claim(id)
        assertEquals(2, next.attempt)
        assertNotEquals(lease.token, next.token)
        assertFalse(success(f.settle(lease, failure)))
        assertTrue(
            success(
                f.settle(
                    next,
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response"),
                )
            )
        )
        assertEquals(
            CollaborationSpaceProvisioningFailure.PROVIDER_IDENTITY_CHANGED,
            f.work(id).lastOutcome?.failure,
        )
    }

    @Test
    fun failureSettlementSurvivesParentPurgeAndOffboardingWithoutChangingDeviceOrCredential() {
        val f = Fixture()
        val id = f.register()
        val lease = f.claim(id)
        val device = f.device(id)
        val at = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val state =
            CollaborationSpaceProviderState(
                space,
                f.account,
                id,
                f.session,
                device.matrixUserId,
                device.matrixDeviceId,
                0,
                at,
                at,
                at.plusSeconds(300),
            )
        success(
            f.transactions.run(f.actor) {
                f.credentials.create(
                    CollaborationSpaceProviderSession(state, "fixture-existing-token")
                )
            }
        )
        owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.account,
        )
        owner.update("update accounts set active=false where id=?", f.account)
        owner.update("delete from native_sessions where id=?", f.session)
        assertTrue(
            success(f.settle(lease, Failure(FailureKind.UNAUTHENTICATED, "session_revoked")))
        )
        assertEquals(
            CollaborationSpaceProvisioningFailure.ACCESS_DENIED,
            f.work(id).lastOutcome?.failure,
        )
        assertEquals(device, f.device(id))
        assertEquals(state, f.state(id))
        assertFalse(
            success(
                f.settle(
                    lease,
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable"),
                )
            )
        )
        assertEquals(
            1,
            owner.queryForObject(
                "select count(*) from audit_entries where actor_id=? and action='collaboration.space_provisioning_failed'",
                Int::class.java,
                f.account,
            ),
        )
    }

    @Test
    fun eighthClaimIsTerminalAndNeverSchedulesANinthProviderAttempt() {
        val f = Fixture()
        val id = f.register()
        var lease =
            requireNotNull(success(f.provisioning.claim(UUID.randomUUID(), 1, 1)).single().lease)
        for (attempt in 2..8) {
            Thread.sleep(1100)
            lease =
                requireNotNull(
                    success(f.provisioning.claim(UUID.randomUUID(), 1, 1)).single().lease
                )
            assertEquals(attempt, lease.attempt)
        }
        assertEquals(id, lease.request.deviceId)
        assertTrue(
            success(
                f.settle(
                    lease,
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable"),
                )
            )
        )
        val final = f.work(id)
        assertEquals(8, final.attempts)
        assertEquals(CollaborationSpaceProvisioningState.FAILED, final.state)
        assertEquals(
            CollaborationSpaceProvisioningFailure.PROVIDER_UNAVAILABLE,
            final.lastOutcome?.failure,
        )
        assertTrue(success(f.provisioning.claim(UUID.randomUUID(), 1, 60)).isEmpty())
    }

    @Test
    fun settlementQueueAndJournalRollBackOnFailureCancellationAndInterruption() {
        for (defer in listOf(false, true)) for (stage in listOf("queue", "journal")) for (mode in
            listOf("failure", "cancel", "interrupt")) {
            val f = Fixture()
            val id = f.register()
            val lease = f.claim(id)
            val original = f.work(id)
            val cause =
                if (defer) Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable")
                else Failure(FailureKind.FORBIDDEN, "collaboration_access_denied")
            fun injected(): Result<Unit> {
                if (mode == "cancel") throw CancellationException("fixture cancellation")
                if (mode == "interrupt") throw InterruptedException("fixture interruption")
                return Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_write_failed"))
            }
            val queue =
                object : CollaborationSpaceProvisioningRepository by f.provisioning {
                    override fun defer(
                        lease: CollaborationSpaceProvisioningLease,
                        delaySeconds: Int,
                    ): Result<Boolean> =
                        f.provisioning.defer(lease, delaySeconds).flatMap { applied ->
                            if (stage == "queue") injected().map { applied }
                            else Result.Success(applied)
                        }

                    override fun finish(
                        lease: CollaborationSpaceProvisioningLease,
                        failure: CollaborationSpaceProvisioningFailure?,
                    ): Result<Boolean> =
                        f.provisioning.finish(lease, failure).flatMap { applied ->
                            if (stage == "queue") injected().map { applied }
                            else Result.Success(applied)
                        }
                }
            val journal = hookJournal { actor, record ->
                f.journal.record(actor, record).flatMap {
                    if (stage == "journal") injected() else Result.Success(Unit)
                }
            }
            fun run() = f.settle(lease, cause, queue, journal)
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { run() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { run() }
                else -> failure(run(), "fixture_write_failed")
            }
            assertEquals(original, f.work(id))
            assertEquals(
                0,
                owner.queryForObject(
                    "select count(*) from audit_entries where actor_id=? and action in ('collaboration.space_provisioning_failed','collaboration.space_provisioning_deferred')",
                    Int::class.java,
                    f.account,
                ),
            )
            assertTrue(success(f.settle(lease, cause)))
            assertEquals(
                1,
                owner.queryForObject(
                    "select count(*) from audit_entries where actor_id=? and action in ('collaboration.space_provisioning_failed','collaboration.space_provisioning_deferred')",
                    Int::class.java,
                    f.account,
                ),
            )
            f.cancel(id)
        }
    }

    private inner class Fixture(verifiedMfa: Boolean = false) {
        val baseAt = now.minusSeconds(120)
        val account = UUID.randomUUID()
        val company = UUID.randomUUID()
        val actor =
            Actor(
                account,
                null,
                emptySet(),
                baseAt.minusSeconds(120),
                UUID.randomUUID(),
                mfaVerifiedAt = if (verifiedMfa) baseAt.minusSeconds(30) else null,
                credentialVersion = 0,
            )
        val clock = MutableClock(baseAt)
        val sql = DSL.using(TransactionAwareDataSourceProxy(runtime), SQLDialect.POSTGRES)
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
        val selections =
            StoredCollaborationSpaceIdentityRepository(
                PostgresCollaborationSpaceIdentityDataSource(sql)
            )
        val operations =
            PostgresOperationRepository(PostgresOperationReceiptDataSource(sql), ObjectMapper())
        val journal =
            PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, ObjectMapper()))
        val identities =
            StoredIdentityRepository(
                PostgresIdentityDataSource(sql),
                object : PasswordDataSource {
                    override fun hash(password: String): String? =
                        error("Selection must not use passwords")

                    override fun matches(password: String, hash: String): Boolean =
                        error("Selection must not use passwords")
                },
                "unused-fixture-hash",
            )
        val sessions =
            StoredNativeSessionRepository(
                PostgresNativeSessionDataSource(sql),
                object : IdentityTokenDataSource {
                    override fun generate(): String = error("Selection must not acquire tokens")

                    override fun hash(token: String): String =
                        error("Selection must not acquire tokens")

                    override fun encrypt(purpose: String, value: String): String =
                        error("Selection must not acquire tokens")

                    override fun decrypt(purpose: String, value: String): String =
                        error("Selection must not acquire tokens")
                },
            )
        val spaces = StoredCollaborationSpaceRepository(PostgresCollaborationSpaceDataSource(sql))
        val registry =
            StoredCollaborationSpaceDeviceRepository(
                PostgresCollaborationSpaceDeviceDataSource(sql)
            )
        val provisioning =
            StoredCollaborationSpaceProvisioningRepository(
                PostgresCollaborationSpaceProvisioningDataSource(sql)
            )
        val credentials =
            EncryptedCollaborationSpaceProviderSessionRepository(
                PostgresCollaborationSpaceProviderSessionDataSource(sql),
                JceIdentityTokenDataSource(
                    parseIdentityKeyring(
                        "fixture",
                        "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 47 }),
                    )
                ),
            )
        val legacyParents =
            StoredCollaborationLegacyParentRepository(
                PostgresCollaborationLegacyParentDataSource(sql)
            )
        val session: UUID

        init {
            fixtures.add(this)
            owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                compact(company).take(20),
                "Fictional company",
            )
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,?)",
                account,
                "$account@example.test",
                "Fictional employee",
            )
            owner.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
            owner.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                company,
                account,
            )
            session = parent()
        }

        fun parent(): UUID {
            val id = UUID.randomUUID()
            owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at,mfa_verified_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)",
                id,
                account,
                compact(id).repeat(2),
                actor.authenticatedAt.toString(),
                baseAt.plusSeconds(3600).toString(),
                baseAt.plusSeconds(900).toString(),
                baseAt.minusSeconds(60).toString(),
                baseAt.minusSeconds(30).toString(),
                actor.mfaVerifiedAt?.toString(),
            )
            return id
        }

        fun activeLegacy(): CollaborationLegacyDevicePosition {
            val device = UUID.randomUUID()
            val at = baseAt.minusSeconds(30).toString()
            owner.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                company,
                device,
                account,
                at,
                at,
            )
            owner.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                company,
                device,
                account,
                session,
                at,
            )
            owner.update(
                "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-not-a-token',?::timestamptz,?::timestamptz,?::timestamptz+interval '5 minutes')",
                company,
                device,
                account,
                "@hris_${compact(company)}_${compact(account)}:$server",
                "HRIS_${compact(device)}",
                at,
                at,
                at,
            )
            owner.update(
                "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
                company,
                device,
            )
            owner.update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?, ?,0,?::timestamptz)",
                company,
                device,
                account,
                "a".repeat(64),
                at,
            )
            return CollaborationLegacyDevicePosition(company, device)
        }

        fun select() {
            success(
                SelectNativeCollaborationSpaceIdentity(
                        selections,
                        spaces,
                        sessions,
                        identities,
                        operations,
                        journal,
                        transactions,
                        CollaborationSpacePolicy(true, 28, 28),
                        CollaborationMatrixPolicy(true, server),
                        IdentitySecurityPolicy(),
                        clock,
                    )
                    .execute(actor, session, 0, space, client, UUID.randomUUID())
            )
        }

        fun seed(
            recent: Boolean = false,
            expiresAt: Instant = Instant.now().plusSeconds(90),
            legacy: CollaborationLegacyDevicePosition? = null,
        ): CollaborationSpaceProvisioning {
            select()
            val id = UUID.randomUUID()
            val identity =
                requireNotNull(
                    success(
                            transactions.run(actor) { selections.selection(space, account, server) }
                        )
                        ?.selected
                )
            val device =
                CollaborationSpaceDevice(
                    id,
                    space,
                    account,
                    identity.matrixUserId,
                    legacy?.let { "HRIS_${compact(it.deviceId)}" } ?: "HRISG_${compact(id)}",
                    if (legacy == null) CollaborationSpaceDeviceOrigin.SPACE
                    else CollaborationSpaceDeviceOrigin.LEGACY,
                    legacy,
                    if (legacy == null) null else "a".repeat(64),
                    session,
                    CollaborationPlatform.ANDROID,
                    "Fictional",
                    CollaborationDeviceState.PROVISIONING,
                    0,
                    baseAt,
                    baseAt,
                    null,
                )
            val at =
                if (recent) Instant.now().truncatedTo(ChronoUnit.MICROS) else baseAt.plusSeconds(30)
            val request =
                CollaborationSpaceProvisioningRequest(
                    space,
                    account,
                    id,
                    session,
                    0,
                    0,
                    0,
                    actor.authenticatedAt,
                    actor.mfaVerifiedAt,
                    actor.correlationId,
                    client,
                    at,
                    expiresAt.truncatedTo(ChronoUnit.MICROS),
                )
            success(
                transactions.run(actor) {
                    registry.register(device).flatMap { provisioning.enqueue(request) }
                }
            )
            return work(id)
        }

        fun register(retained: CollaborationLegacyDevicePosition? = null): UUID {
            select()
            return success(
                    RegisterNativeCollaborationSpaceDevice(
                            registry,
                            provisioning,
                            legacyParents,
                            selections,
                            spaces,
                            sessions,
                            identities,
                            operations,
                            journal,
                            transactions,
                            CollaborationSpacePolicy(true, 28, 28),
                            CollaborationMatrixPolicy(true, server),
                            IdentitySecurityPolicy(),
                            Clock.systemUTC(),
                        )
                        .execute(
                            actor,
                            session,
                            0,
                            space,
                            client,
                            UUID.randomUUID(),
                            CollaborationPlatform.ANDROID,
                            "Fictional",
                            if (retained == null) CollaborationSpaceDeviceOrigin.SPACE
                            else CollaborationSpaceDeviceOrigin.LEGACY,
                            retained,
                            if (retained == null) null else "a".repeat(64),
                        )
                )
                .id
        }

        fun advance(
            lease: CollaborationSpaceProvisioningLease,
            matrix: CollaborationSpaceMatrixRepository,
            registry: CollaborationSpaceDeviceRepository = this.registry,
            provisioning: CollaborationSpaceProvisioningRepository = this.provisioning,
            credentials: CollaborationSpaceProviderSessionRepository = this.credentials,
            journal: ChangeJournalRepository = this.journal,
            clock: Clock = Clock.systemUTC(),
        ): Result<Unit> =
            AdvanceCollaborationSpaceProvisioning(
                    registry,
                    provisioning,
                    credentials,
                    matrix,
                    selections,
                    spaces,
                    sessions,
                    identities,
                    journal,
                    transactions,
                    CollaborationSpacePolicy(true, 28, 28),
                    CollaborationMatrixPolicy(true, server),
                    IdentitySecurityPolicy(),
                    clock,
                )
                .execute(lease)

        fun settle(
            lease: CollaborationSpaceProvisioningLease,
            failure: Failure,
            provisioning: CollaborationSpaceProvisioningRepository = this.provisioning,
            journal: ChangeJournalRepository = this.journal,
        ): Result<Boolean> =
            SettleCollaborationSpaceProvisioningFailure(
                    provisioning,
                    identities,
                    journal,
                    transactions,
                    Clock.systemUTC(),
                )
                .execute(lease, failure)

        fun claim(device: UUID): CollaborationSpaceProvisioningLease {
            val lease =
                requireNotNull(success(provisioning.claim(UUID.randomUUID(), 1, 60)).single().lease)
            assertEquals(device, lease.request.deviceId)
            return lease
        }

        fun device(id: UUID): CollaborationSpaceDevice =
            requireNotNull(
                success(transactions.run(actor) { registry.metadata(space, account, id) })
            )

        fun state(id: UUID): CollaborationSpaceProviderState? =
            success(transactions.run(actor) { credentials.metadata(space, account, id) })

        fun credential(id: UUID): CollaborationSpaceProviderSession {
            val state = requireNotNull(state(id))
            return success(transactions.run(actor) { credentials.credential(state) })
        }

        fun provider(device: UUID, action: () -> Unit = {}): MatrixStub {
            val expected = device(device)
            return MatrixStub { request ->
                assertEquals(space, request.spaceId)
                assertEquals(account, request.accountId)
                assertEquals(device, request.deviceId)
                assertNull(request.retainedDevice)
                assertTrue(request.expiresAt <= request.issuedAt.plusSeconds(60))
                for ((table, key, id) in
                    listOf(
                        Triple("accounts", "id", account),
                        Triple("native_sessions", "id", session),
                        Triple("collaboration_space_devices", "id", device),
                        Triple("collaboration_space_provisioning", "device_id", device),
                    )) assertEquals(
                    1,
                    owner
                        .queryForList("select $key from $table where $key=? for update nowait", id)
                        .size,
                )
                action()
                Result.Success(
                    CollaborationMatrixCredential(
                        expected.matrixUserId,
                        expected.matrixDeviceId,
                        "fictional-token-${UUID.randomUUID()}",
                        Instant.now().plusSeconds(590),
                    )
                )
            }
        }

        fun work(device: UUID): CollaborationSpaceProvisioning =
            requireNotNull(
                success(transactions.run(actor) { provisioning.metadata(space, account, device) })
            )

        fun cancel(device: UUID): Boolean {
            val revision = work(device).request.revision
            return success(
                transactions.run(actor) { provisioning.cancel(space, account, device, revision) }
            )
        }

        fun activatedCount(expected: Int) {
            for ((table, column) in
                listOf(
                    "audit_entries" to "action",
                    "platform_audit_metadata" to "action",
                    "outbox_events" to "event_type",
                )) assertEquals(
                expected,
                owner.queryForObject(
                    "select count(*) from $table where actor_id=? and $column='collaboration.space_device_activated'",
                    Int::class.java,
                    account,
                ),
            )
        }
    }

    private class MatrixStub(
        val action: (CollaborationSpaceMatrixLogin) -> Result<CollaborationMatrixCredential>
    ) : CollaborationSpaceMatrixRepository {
        val calls = AtomicInteger()

        override fun login(
            request: CollaborationSpaceMatrixLogin
        ): Result<CollaborationMatrixCredential> {
            calls.incrementAndGet()
            return action(request)
        }
    }

    private fun hookJournal(
        action: (Actor, ChangeRecord) -> Result<Unit>
    ): ChangeJournalRepository =
        object : ChangeJournalRepository {
            override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                action(actor, change)
        }

    private class MutableClock(var at: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = at
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result) {
            if (result is Result.Failed) result.failure.code else "Unexpected result"
        }
        return (result as Result.Success).value
    }

    private fun failure(result: Result<*>, code: String) {
        assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(code, (result as Result.Failed).failure.code)
    }
}
