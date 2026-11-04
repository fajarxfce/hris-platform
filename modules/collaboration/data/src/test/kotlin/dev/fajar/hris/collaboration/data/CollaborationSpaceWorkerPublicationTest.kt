package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validateCollaborationDeviceKeys
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
            "grant update on accounts,native_sessions,collaboration_space_devices,collaboration_space_provisioning,collaboration_space_provider_sessions,collaboration_legacy_runtime,collaboration_legacy_runtime_leases to space_advance_command_fixture"
        )
        owner.execute(
            "grant insert on collaboration_space_provider_sessions,collaboration_space_provisioning,collaboration_space_identities,collaboration_space_devices,collaboration_space_device_sessions,collaboration_legacy_runtime,collaboration_legacy_runtime_leases,operation_receipts,audit_entries,platform_audit_metadata,outbox_events to space_advance_command_fixture"
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
    fun completedNativeRegistrationFreezeAndDrainRetainReceiptsAcrossRefreshAndMetadataUpdates() {
        val f = Fixture(verifiedMfa = true)
        val original = f.activeLegacy(signed = true)
        f.gateway(original)
        f.select()
        val registration = UUID.randomUUID()
        val registered = success(f.registrationCommand(original, registration))
        val id = registered.id
        val freeze = UUID.randomUUID()
        val frozen = success(f.freezeCommand(id, freeze))
        success(f.advance(f.claim(id), f.provider(id)))
        val completed = requireNotNull(f.completion(original))
        val head = f.work(id)
        val credential = f.credential(id)
        val old = f.originalSnapshot()
        assertEquals(
            registered.copy(replayed = true),
            success(f.registrationCommand(original, registration)),
        )
        assertEquals(
            MutationReceipt(id, 1),
            success(f.registrationCommand(original, UUID.randomUUID())),
        )
        assertEquals(frozen.copy(replayed = true), success(f.freezeCommand(id, freeze)))
        assertEquals(frozen, success(f.freezeCommand(id, UUID.randomUUID())))
        assertEquals(0, success(f.drainCommand(id)).activeRequests)
        owner.update(
            "update native_sessions set version=version+1,rotated_at=clock_timestamp() where id=?",
            f.session,
        )
        assertEquals(
            registered.copy(replayed = true),
            success(f.registrationCommand(original, registration, nativeVersion = 1)),
        )
        assertEquals(
            frozen.copy(replayed = true),
            success(f.freezeCommand(id, freeze, nativeVersion = 1)),
        )
        assertEquals(0, success(f.drainCommand(id, nativeVersion = 1)).activeRequests)
        assertEquals(old, f.originalSnapshot())
        assertEquals(head, f.work(id))
        assertEquals(credential, f.credential(id))
        assertEquals(completed, f.completion(original))
        // Existing terminal device rules allow later metadata versions but preserve revokedAt.
        owner.update(
            "update collaboration_devices set version=version+1,label='Renamed old device',updated_at=clock_timestamp() where company_id=? and id=?",
            f.company,
            original.deviceId,
        )
        owner.update(
            "update collaboration_space_devices set version=version+1,label='Renamed group device',updated_at=clock_timestamp() where id=?",
            id,
        )
        assertEquals(
            registered.copy(replayed = true),
            success(f.registrationCommand(original, registration, nativeVersion = 1)),
        )
        assertEquals(
            MutationReceipt(id, 2),
            success(f.registrationCommand(original, UUID.randomUUID(), nativeVersion = 1)),
        )
        assertEquals(
            frozen.copy(replayed = true),
            success(f.freezeCommand(id, freeze, nativeVersion = 1)),
        )
        assertEquals(0, success(f.drainCommand(id, nativeVersion = 1)).activeRequests)
        assertEquals(completed, f.completion(original))
        f.activatedCount(1)
        assertEquals(
            1,
            owner.queryForObject(
                "select count(*) from audit_entries where actor_id=? and action='collaboration.legacy_runtime_frozen'",
                Int::class.java,
                f.account,
            ),
        )
    }

    @Test
    fun completedRetryReturnsItsOriginalReceiptButNeverQueuesAnotherActivation() {
        val f = Fixture()
        val original = f.activeLegacy(signed = true)
        val seeded = f.seed(legacy = original)
        val id = seeded.request.deviceId
        assertTrue(f.cancel(id))
        val operation = UUID.randomUUID()
        val receipt = success(f.retryCommand(id, operation))
        assertEquals(1L, receipt.version)
        f.freeze(id)
        success(f.advance(f.claim(id), f.provider(id)))
        val head = f.work(id)
        val credential = f.credential(id)
        assertEquals(receipt.copy(replayed = true), success(f.retryCommand(id, operation)))
        owner.update(
            "update native_sessions set version=version+1,rotated_at=clock_timestamp() where id=?",
            f.session,
        )
        assertEquals(
            receipt.copy(replayed = true),
            success(f.retryCommand(id, operation, nativeVersion = 1)),
        )
        failure(
            f.retryCommand(id, UUID.randomUUID(), nativeVersion = 1, revision = 1),
            "collaboration_provisioning_changed",
        )
        assertEquals(head, f.work(id))
        assertEquals(credential, f.credential(id))
        assertEquals(1L, requireNotNull(f.completion(original)).provisioningRevision)
        f.activatedCount(1)
    }

    @Test
    fun completedReplayRejectsMissingForeignAndIncoherentCompletionWithoutCreatingReceipts() {
        val f = Fixture()
        val original = f.activeLegacy(signed = true)
        val id = f.register(original)
        f.freeze(id)
        success(f.advance(f.claim(id), f.provider(id)))
        val done = requireNotNull(f.completion(original))
        val count = f.receiptCount()
        val old = f.originalSnapshot()
        for (invalid in
            listOf(
                null,
                done.copy(owner = done.owner.copy(accountId = UUID.randomUUID())),
                done.copy(owner = done.owner.copy(companyId = UUID.randomUUID())),
                done.copy(owner = done.owner.copy(nativeSessionId = UUID.randomUUID())),
                done.copy(target = done.target.copy(deviceId = UUID.randomUUID())),
                done.copy(target = done.target.copy(spaceId = UUID.randomUUID())),
                done.copy(fingerprint = "b".repeat(64)),
                done.copy(matrixUserId = "@other:$server"),
                done.copy(matrixDeviceId = "HRIS_other"),
                done.copy(nativeSessionVersion = 1),
                done.copy(targetDeviceVersion = 999999),
                done.copy(sourceDeviceVersion = done.sourceDeviceVersion + 1),
                done.copy(sourceProviderGeneration = done.sourceProviderGeneration + 1),
                done.copy(provisioningRevision = done.provisioningRevision + 1),
                done.copy(completedAt = done.completedAt.minusNanos(1000)),
            )) {
            val substituted =
                object : CollaborationLegacyRuntimeCompletionRepository by f.completions {
                    override fun find(
                        owner: CollaborationLegacyRuntimeOwner
                    ): Result<CollaborationLegacyRuntimeCompletion?> = Result.Success(invalid)
                }
            assertInstanceOf(
                Result.Failed::class.java,
                f.registrationCommand(original, UUID.randomUUID(), completions = substituted),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                f.freezeCommand(id, UUID.randomUUID(), completions = substituted),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                f.drainCommand(id, completions = substituted),
            )
            assertEquals(count, f.receiptCount())
            assertEquals(old, f.originalSnapshot())
        }
        f.activatedCount(1)
    }

    @Test
    fun completedReplayStillRequiresCurrentMembershipAndUnrevokedTarget() {
        val f = Fixture()
        val original = f.activeLegacy(signed = true)
        f.select()
        val registration = UUID.randomUUID()
        val id = success(f.registrationCommand(original, registration)).id
        val freeze = UUID.randomUUID()
        success(f.freezeCommand(id, freeze))
        success(f.advance(f.claim(id), f.provider(id)))
        val receipts = f.receiptCount()
        owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.account,
        )
        for (result in
            listOf(
                f.registrationCommand(original, registration),
                f.freezeCommand(id, freeze),
                f.drainCommand(id),
            )) failure(result, "collaboration_access_denied")
        owner.update(
            "update company_memberships set active=true,version=version+1 where account_id=?",
            f.account,
        )
        owner.update(
            "update collaboration_space_devices set state='REVOKED',version=version+1,revoked_at=clock_timestamp(),updated_at=clock_timestamp() where id=?",
            id,
        )
        for (result in
            listOf(
                f.registrationCommand(original, registration),
                f.freezeCommand(id, freeze),
                f.drainCommand(id),
            )) assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(receipts, f.receiptCount())
        assertNotNull(f.completion(original))
    }

    @Test
    fun completedReplayReceiptRollsBackOnLateAdmissionReadFailureCancellationAndInterruption() {
        for (command in listOf("register", "freeze")) for (mode in
            listOf("late", "failure", "cancel", "interrupt", "flag")) {
            val f = Fixture()
            val original = f.activeLegacy(signed = true)
            val id = f.register(original)
            f.freeze(id)
            success(f.advance(f.claim(id), f.provider(id)))
            val count = f.receiptCount()
            val old = f.originalSnapshot()
            val operation = UUID.randomUUID()
            val clock = MutableClock(Instant.now())
            val cancellation = CancellationException("fixture replay cancellation")
            val interruption = InterruptedException("fixture replay interruption")
            val operations =
                object : OperationRepository by f.operations {
                    override fun record(
                        actor: Actor,
                        key: OperationKey,
                        receipt: MutationReceipt,
                    ): Result<Unit> =
                        f.operations.record(actor, key, receipt).flatMap {
                            when (mode) {
                                "late" -> {
                                    clock.at = clock.at.plusSeconds(31)
                                    Result.Success(Unit)
                                }
                                "cancel" -> throw cancellation
                                "interrupt" -> throw interruption
                                "flag" -> {
                                    Thread.currentThread().interrupt()
                                    Result.Success(Unit)
                                }
                                else -> Result.Success(Unit)
                            }
                        }
                }
            val reads = AtomicInteger()
            val completions =
                object : CollaborationLegacyRuntimeCompletionRepository by f.completions {
                    override fun find(
                        owner: CollaborationLegacyRuntimeOwner
                    ): Result<CollaborationLegacyRuntimeCompletion?> {
                        if (reads.incrementAndGet() == 2 && mode == "failure")
                            return Result.Failed(
                                Failure(FailureKind.UNAVAILABLE, "fixture_completion_unavailable")
                            )
                        return f.completions.find(owner)
                    }
                }
            fun run() =
                if (command == "register")
                    f.registrationCommand(
                        original,
                        operation,
                        completions = completions,
                        operations = operations,
                        clock = clock,
                    )
                else
                    f.freezeCommand(
                        id,
                        operation,
                        completions = completions,
                        operations = operations,
                        clock = clock,
                    )
            try {
                when (mode) {
                    "cancel" ->
                        assertSame(
                            cancellation,
                            assertThrows(CancellationException::class.java) { run() },
                        )
                    "interrupt" ->
                        assertSame(
                            interruption,
                            assertThrows(InterruptedException::class.java) { run() },
                        )
                    "flag" -> {
                        assertThrows(InterruptedException::class.java) { run() }
                        assertTrue(Thread.currentThread().isInterrupted)
                    }
                    else -> assertInstanceOf(Result.Failed::class.java, run())
                }
            } finally {
                Thread.interrupted()
            }
            assertEquals(count, f.receiptCount())
            assertEquals(old, f.originalSnapshot())
            if (command == "register") success(f.registrationCommand(original, operation))
            else success(f.freezeCommand(id, operation))
            assertEquals(count + 1, f.receiptCount())
            f.activatedCount(1)
        }
    }

    @Test
    fun retainedActivationMatchesActualSignedReadbackAndRetiresOnlyItsOriginalGateway() {
        for (withGateway in listOf(false, true)) {
            val f = Fixture(verifiedMfa = true)
            val original = f.activeLegacy(signed = true)
            val gateway = if (withGateway) f.gateway(original) else null
            val id = f.register(original)
            f.freeze(id)
            val lease = f.claim(id)
            val before = f.originalSnapshot()
            val queried = AtomicInteger()
            val keys =
                f.keyProvider { user, device, token, deadline ->
                    queried.incrementAndGet()
                    assertEquals(f.retainedKeys!!.userId, user)
                    assertEquals(f.retainedKeys!!.deviceId, device)
                    assertTrue(token.startsWith("fictional-token-"))
                    assertTrue(deadline <= lease.until)
                    for ((table, column, value) in
                        listOf(
                            Triple("accounts", "id", f.account),
                            Triple("native_sessions", "id", f.session),
                            Triple("collaboration_space_devices", "id", id),
                            Triple("collaboration_legacy_runtime", "device_id", original.deviceId),
                        )) assertEquals(
                        1,
                        owner
                            .queryForList(
                                "select $column from $table where $column=? for update nowait",
                                value,
                            )
                            .size,
                    )
                    Result.Success(f.retainedKeys!!.device)
                }
            val matrix = f.provider(id)
            success(f.advance(lease, matrix, keys = keys))
            assertEquals(1, matrix.calls.get())
            assertEquals(1, queried.get())
            val completion = requireNotNull(f.completion(original))
            assertEquals(f.runtimeOwner(original), completion.owner)
            assertEquals(CollaborationLegacyRuntimeTarget(space, id), completion.target)
            assertEquals(f.fingerprint(), completion.fingerprint)
            assertEquals(2L, completion.sourceDeviceVersion)
            assertEquals(1L, completion.sourceProviderGeneration)
            assertEquals(gateway, completion.sourceGateway?.id)
            assertEquals(if (withGateway) 1L else null, completion.sourceGateway?.version)
            assertEquals(1L, f.device(id).version)
            assertEquals(CollaborationDeviceState.ACTIVE, f.device(id).state)
            assertEquals(CollaborationSpaceProvisioningState.SUCCEEDED, f.work(id).state)
            assertEquals(f.retainedKeys!!.deviceId, f.credential(id).state.matrixDeviceId)
            val after = f.originalSnapshot()
            assertEquals(
                before.getValue("collaboration_device_key_publications"),
                after.getValue("collaboration_device_key_publications"),
            )
            assertEquals("REVOKED", after.getValue("collaboration_devices").single()["state"])
            assertNull(
                after.getValue("collaboration_provider_sessions").single()["token_encrypted"]
            )
            if (withGateway)
                for (column in
                    listOf("access_hash", "refresh_hash", "replay_encrypted")) assertNull(
                    after.getValue("collaboration_gateway_sessions").single()[column]
                )
            f.activatedCount(1)
            assertInstanceOf(Result.Failed::class.java, f.advance(lease, matrix, keys = keys))
            assertEquals(1, matrix.calls.get())
            assertEquals(1, queried.get())
            assertEquals(completion, f.completion(original))
        }
    }

    @Test
    fun retainedActivationWaitsForExplicitFreezeAndFiniteOldRequestsWithoutCallingProvider() {
        for (phase in listOf("absent", "legacy", "busy")) {
            val f = Fixture()
            val original = f.activeLegacy(signed = true)
            val id = f.register(original)
            val originalOwner = f.runtimeOwner(original)
            var oldLease: CollaborationLegacyRuntimeLease? = null
            if (phase != "absent")
                success(
                    f.transactions.run(f.actor) {
                        f.runtimes.ensure(
                            originalOwner,
                            Instant.now().truncatedTo(ChronoUnit.MICROS),
                        )
                    }
                )
            if (phase == "busy") {
                oldLease =
                    requireNotNull(
                        success(
                            f.transactions.run(f.actor.copy(companyId = f.company)) {
                                f.runtimes.claim(
                                    originalOwner,
                                    Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.MICROS),
                                )
                            }
                        )
                    )
                f.freeze(id)
            }
            var lease = f.claim(id)
            val matrix = f.provider(id)
            val before = f.originalSnapshot()
            val failed = f.advance(lease, matrix)
            failure(failed, "collaboration_runtime_busy")
            assertEquals(0, matrix.calls.get())
            assertNull(f.state(id))
            assertNull(f.completion(original))
            assertEquals(before, f.originalSnapshot())
            if (phase == "absent") {
                assertTrue(success(f.settle(lease, (failed as Result.Failed).failure)))
                val waiting = f.work(id)
                assertEquals(CollaborationSpaceProvisioningState.QUEUED, waiting.state)
                assertNull(waiting.lastOutcome)
                assertEquals(
                    "PUBLICATION_CHANGED",
                    owner.queryForObject(
                        "select details->>'failure' from audit_entries where actor_id=? and action='collaboration.space_provisioning_deferred'",
                        String::class.java,
                        f.account,
                    ),
                )
                assertEquals(1, waiting.attempts)
                assertTrue(waiting.availableAt > Instant.now())
                assertTrue(waiting.availableAt <= Instant.now().plusSeconds(3))
                assertTrue(
                    success(
                            ClaimCollaborationSpaceProvisioning(
                                    f.provisioning,
                                    CollaborationSpacePolicy(true, 28, 28),
                                    CollaborationMatrixPolicy(true, server),
                                )
                                .execute(UUID.randomUUID(), 1)
                        )
                        .isEmpty()
                )
                f.freeze(id)
                Thread.sleep(
                    (Duration.between(Instant.now(), waiting.availableAt).toMillis() + 25).coerceIn(
                        1,
                        3025,
                    )
                )
                val retry = f.claim(id)
                assertEquals(2, retry.attempt)
                assertEquals(lease.request, retry.request)
                assertNotEquals(lease.token, retry.token)
                lease = retry
            }
            if (oldLease != null)
                success(f.transactions.run(f.actor) { f.runtimes.release(oldLease) })
            if (phase == "legacy") f.freeze(id)
            success(f.advance(lease, matrix))
            assertEquals(1, matrix.calls.get())
            f.activatedCount(1)
        }
    }

    @Test
    fun retainedProviderErrorsAndKeysCannotPublishAfterNativeOrSourceChanges() {
        for (stage in listOf("login", "keys")) for (change in
            listOf("native", "gateway", "provider", "offboard")) {
            val f = Fixture()
            val original = f.activeLegacy(signed = true)
            f.gateway(original)
            val id = f.register(original)
            f.freeze(id)
            val lease = f.claim(id)
            var changed = false
            fun changeOnce() {
                if (changed) return
                changed = true
                when (change) {
                    "native" ->
                        owner.update(
                            "update native_sessions set version=version+1,rotated_at=clock_timestamp() where id=?",
                            f.session,
                        )
                    "gateway" ->
                        owner.update(
                            "update collaboration_gateway_sessions set version=version+1,rotated_at=clock_timestamp(),access_hash=?,refresh_hash=?,replay_until=clock_timestamp()+interval '10 seconds',replay_encrypted='fictional-late-gateway-envelope' where account_id=?",
                            compact(UUID.randomUUID()).repeat(2),
                            compact(UUID.randomUUID()).repeat(2),
                            f.account,
                        )
                    "provider" ->
                        owner.update(
                            "update collaboration_provider_sessions set generation=generation+1,updated_at=clock_timestamp() where account_id=?",
                            f.account,
                        )
                    "offboard" ->
                        owner.update(
                            "update company_memberships set active=false,version=version+1 where account_id=?",
                            f.account,
                        )
                }
            }
            val readCalls = AtomicInteger()
            val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_error"))
            val matrix =
                if (stage == "login")
                    MatrixStub {
                        changeOnce()
                        failure
                    }
                else f.provider(id)
            val keys =
                f.keyProvider { _, _, _, _ ->
                    readCalls.incrementAndGet()
                    changeOnce()
                    failure
                }
            val result = f.advance(lease, matrix, keys = keys)
            assertInstanceOf(Result.Failed::class.java, result)
            assertNotEquals("fixture_provider_error", (result as Result.Failed).failure.code)
            assertEquals(if (stage == "login") 0 else 1, readCalls.get())
            assertEquals(
                0,
                owner.queryForObject(
                    "select count(*) from collaboration_legacy_runtime_completions where account_id=?",
                    Int::class.java,
                    f.account,
                ),
            )
            assertEquals(
                "ACTIVE",
                owner.queryForObject(
                    "select state from collaboration_devices where account_id=?",
                    String::class.java,
                    f.account,
                ),
            )
            assertEquals(
                "PROVISIONING",
                owner.queryForObject(
                    "select state from collaboration_space_devices where account_id=?",
                    String::class.java,
                    f.account,
                ),
            )
            assertEquals(
                0,
                owner.queryForObject(
                    "select count(*) from collaboration_space_provider_sessions where account_id=?",
                    Int::class.java,
                    f.account,
                ),
            )
            f.activatedCount(0)
        }
    }

    @Test
    fun retainedProviderCancellationAndInterruptionNeverPublishOrContinueReadback() {
        for (stage in listOf("login", "keys")) for (mode in listOf("cancel", "interrupt", "flag")) {
            val f = Fixture()
            val original = f.activeLegacy(signed = true)
            val id = f.register(original)
            f.freeze(id)
            val lease = f.claim(id)
            val before = f.originalSnapshot()
            val cancellation = CancellationException("fixture cancellation")
            val interruption = InterruptedException("fixture interruption")
            fun interrupt() {
                when (mode) {
                    "cancel" -> throw cancellation
                    "interrupt" -> throw interruption
                    else -> Thread.currentThread().interrupt()
                }
            }
            val matrix = f.provider(id) { if (stage == "login") interrupt() }
            val reads = AtomicInteger()
            val keys =
                f.keyProvider { _, _, _, _ ->
                    reads.incrementAndGet()
                    interrupt()
                    Result.Success(f.retainedKeys!!.device)
                }
            fun run() = f.advance(lease, matrix, keys = keys)
            try {
                when (mode) {
                    "cancel" ->
                        assertSame(
                            cancellation,
                            assertThrows(CancellationException::class.java) { run() },
                        )
                    "interrupt" ->
                        assertSame(
                            interruption,
                            assertThrows(InterruptedException::class.java) { run() },
                        )
                    else -> {
                        assertThrows(InterruptedException::class.java) { run() }
                        assertTrue(Thread.currentThread().isInterrupted)
                    }
                }
            } finally {
                Thread.interrupted()
            }
            assertEquals(if (stage == "login") 0 else 1, reads.get())
            assertEquals(before, f.originalSnapshot())
            assertNull(f.state(id))
            assertNull(f.completion(original))
            f.activatedCount(0)
            success(f.advance(lease, f.provider(id)))
            f.activatedCount(1)
        }
    }

    @Test
    fun retainedMissingSubstitutedAndInvalidSignedKeysLeaveOldStateRecoverable() {
        for (shape in listOf("absent", "user", "device", "signature", "new-keys")) {
            val f = Fixture()
            val original = f.activeLegacy(signed = true)
            val id = f.register(original)
            f.freeze(id)
            val lease = f.claim(id)
            val before = f.originalSnapshot()
            val valid = f.retainedKeys!!.device
            val wrong =
                when (shape) {
                    "absent" -> null
                    "user" -> valid.copy(userId = "@other:$server")
                    "device" -> valid.copy(deviceId = "HRIS_other")
                    "signature" -> valid.copy(signatures = emptyMap())
                    else -> MatrixKeyFixture(valid.userId, valid.deviceId).device
                }
            failure(
                f.advance(
                    lease,
                    f.provider(id),
                    keys = f.keyProvider { _, _, _, _ -> Result.Success(wrong) },
                ),
                "collaboration_provider_invalid_response",
            )
            assertEquals(before, f.originalSnapshot())
            assertNull(f.state(id))
            assertNull(f.completion(original))
            assertEquals(CollaborationSpaceProvisioningState.RUNNING, f.work(id).state)
            f.activatedCount(0)
            success(f.advance(lease, f.provider(id)))
            f.activatedCount(1)
        }
    }

    @Test
    fun retainedPublicationRollsBackOriginalRetirementAndNewStateAtEveryWriteBoundary() {
        for (stage in listOf("credential", "device", "queue", "completion", "journal")) for (mode in
            listOf("failure", "cancel", "interrupt", "flag")) {
            val f = Fixture()
            val original = f.activeLegacy(signed = true)
            f.gateway(original)
            val id = f.register(original)
            f.freeze(id)
            val lease = f.claim(id)
            val before = f.originalSnapshot()
            val device = f.device(id)
            val head = f.work(id)
            val cancellation = CancellationException("fixture cancellation")
            val interruption = InterruptedException("fixture interruption")
            fun injected(): Result<Unit> =
                when (mode) {
                    "cancel" -> throw cancellation
                    "interrupt" -> throw interruption
                    "flag" -> {
                        Thread.currentThread().interrupt()
                        Result.Success(Unit)
                    }
                    else -> Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_write_failed"))
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
                        f.provisioning.finish(lease, failure).flatMap { done ->
                            (if (stage == "queue") injected() else Result.Success(Unit)).map {
                                done
                            }
                        }
                }
            val completion =
                object : CollaborationLegacyRuntimeCompletionRepository by f.completions {
                    override fun complete(
                        request: CompleteCollaborationLegacyRuntimeRequest
                    ): Result<CollaborationLegacyRuntimeCompletion> =
                        f.completions.complete(request).flatMap { done ->
                            (if (stage == "completion") injected() else Result.Success(Unit)).map {
                                done
                            }
                        }
                }
            val journal = hookJournal { actor, record ->
                f.journal.record(actor, record).flatMap {
                    if (stage == "journal") injected() else Result.Success(Unit)
                }
            }
            fun run() =
                f.advance(
                    lease,
                    f.provider(id),
                    registry,
                    queue,
                    credentials,
                    journal,
                    completions = completion,
                )
            try {
                when (mode) {
                    "cancel" ->
                        assertSame(
                            cancellation,
                            assertThrows(CancellationException::class.java) { run() },
                        )
                    "interrupt" ->
                        assertSame(
                            interruption,
                            assertThrows(InterruptedException::class.java) { run() },
                        )
                    "flag" -> {
                        assertThrows(InterruptedException::class.java) { run() }
                        assertTrue(Thread.currentThread().isInterrupted)
                    }
                    else -> failure(run(), "fixture_write_failed")
                }
            } finally {
                Thread.interrupted()
            }
            assertEquals(before, f.originalSnapshot())
            assertEquals(device, f.device(id))
            assertEquals(head, f.work(id))
            assertNull(f.state(id))
            assertNull(f.completion(original))
            f.activatedCount(0)
            success(f.advance(lease, f.provider(id)))
            f.activatedCount(1)
        }
    }

    @Test
    fun retainedLateAdmissionAfterCompletionRollsBackBothSidesAndCanRetry() {
        val f = Fixture()
        val original = f.activeLegacy(signed = true)
        f.gateway(original)
        val id = f.register(original)
        f.freeze(id)
        val lease = f.claim(id)
        val before = f.originalSnapshot()
        val clock = MutableClock(Instant.now())
        val journal = hookJournal { actor, record ->
            f.journal.record(actor, record).map { clock.at = lease.until }
        }
        assertInstanceOf(
            Result.Failed::class.java,
            f.advance(lease, f.provider(id), journal = journal, clock = clock),
        )
        assertEquals(before, f.originalSnapshot())
        assertNull(f.state(id))
        assertNull(f.completion(original))
        f.activatedCount(0)
        success(f.advance(lease, f.provider(id)))
        f.activatedCount(1)
    }

    @Test
    fun retainedCompetingWorkersPublishOneTransferAndNeverReplaceTheWinningCredential() {
        val f = Fixture()
        val original = f.activeLegacy(signed = true)
        val id = f.register(original)
        f.freeze(id)
        val lease = f.claim(id)
        val device = f.device(id)
        val barrier = CyclicBarrier(2)
        val tokens = ConcurrentHashMap.newKeySet<String>()
        val matrix = MatrixStub {
            assertEquals(original, it.retainedDevice)
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
            assertEquals(0L, f.credential(id).state.generation)
            assertNotNull(f.completion(original))
            assertEquals(1L, f.device(id).version)
            f.activatedCount(1)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun retainedWorkerUsesActualSynapseReadbackAndKeepsKeysAcrossProviderRestart() {
        MatrixProviderFixture("10m").use { provider ->
            provider.start()
            val f = Fixture()
            val original = f.activeLegacy(signed = true)
            val at = Instant.now()
            val oldCredential =
                success(
                    provider.repository.login(
                        CollaborationMatrixLogin(
                            f.company,
                            f.account,
                            original.deviceId,
                            at,
                            at.plusSeconds(60),
                        )
                    )
                )
            fun keyRepository() =
                InternalCollaborationMatrixKeyRepository(
                    dev.fajar.hris.collaboration.data.matrix.HttpMatrixClientDataSource(
                        provider.client,
                        provider.config,
                        Clock.systemUTC(),
                    )
                )
            success(
                keyRepository()
                    .upload(
                        CollaborationMatrixKeyUpload(requireNotNull(f.retainedKeys).device),
                        oldCredential.accessToken,
                        Instant.now().plusSeconds(20),
                    )
            )
            // Synapse's normal per-account login limiter remains enabled in this fixture.
            Thread.sleep(11000)
            val id = f.register(original)
            f.freeze(id)
            val lease = f.claim(id)
            val matrix =
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
            success(f.advance(lease, matrix, keys = keyRepository()))
            val current = f.credential(id)
            assertEquals(oldCredential.userId, current.state.matrixUserId)
            assertEquals(oldCredential.deviceId, current.state.matrixDeviceId)
            assertNotEquals(oldCredential.accessToken, current.accessToken)
            val completion = requireNotNull(f.completion(original))
            assertEquals(f.fingerprint(), completion.fingerprint)
            fun checkKeys(token: String) {
                val key =
                    requireNotNull(
                        success(
                            keyRepository()
                                .ownDeviceKeys(
                                    current.state.matrixUserId,
                                    current.state.matrixDeviceId,
                                    token,
                                    Instant.now().plusSeconds(20),
                                )
                        )
                    )
                assertEquals(
                    f.fingerprint(),
                    success(
                        validateCollaborationDeviceKeys(
                            key,
                            current.state.matrixUserId,
                            current.state.matrixDeviceId,
                        )
                    ),
                )
            }
            checkKeys(current.accessToken)
            provider.restart()
            checkKeys(current.accessToken)
            // Retirement stops HRIS gateways, never logs the provider device out or deletes keys.
            checkKeys(oldCredential.accessToken)
            f.activatedCount(1)
        }
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
            "collaboration_runtime_busy",
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
        val runtimes =
            StoredCollaborationLegacyRuntimeRepository(
                PostgresCollaborationLegacyRuntimeDataSource(sql)
            )
        val gateways =
            StoredCollaborationLegacyGatewayRepository(
                PostgresCollaborationLegacyGatewayDataSource(sql)
            )
        val completions =
            StoredCollaborationLegacyRuntimeCompletionRepository(
                PostgresCollaborationLegacyRuntimeCompletionDataSource(sql)
            )
        var retainedKeys: MatrixKeyFixture? = null
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

        fun activeLegacy(signed: Boolean = false): CollaborationLegacyDevicePosition {
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
            if (signed)
                retainedKeys =
                    MatrixKeyFixture(
                        "@hris_${compact(company)}_${compact(account)}:$server",
                        "HRIS_${compact(device)}",
                    )
            owner.update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?, ?,0,?::timestamptz)",
                company,
                device,
                account,
                fingerprint(),
                at,
            )
            return CollaborationLegacyDevicePosition(company, device)
        }

        fun fingerprint(): String =
            retainedKeys?.let {
                success(validateCollaborationDeviceKeys(it.device, it.userId, it.deviceId))
            } ?: "a".repeat(64)

        fun runtimeOwner(original: CollaborationLegacyDevicePosition) =
            CollaborationLegacyRuntimeOwner(original.companyId, original.deviceId, account, session)

        fun freeze(id: UUID) {
            success(
                FreezeNativeCollaborationLegacyRuntime(
                        registry,
                        legacyParents,
                        selections,
                        runtimes,
                        provisioning,
                        completions,
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
                        id,
                        0,
                        fingerprint(),
                    )
            )
        }

        fun gateway(original: CollaborationLegacyDevicePosition): UUID {
            val id = UUID.randomUUID()
            val at = baseAt.minusSeconds(20)
            owner.update(
                "insert into collaboration_gateway_sessions(company_id,id,account_id,device_id,native_session_id,platform,client_build,version,access_hash,refresh_hash,created_at,rotated_at,expires_at,access_expires_at,replay_kind,bootstrap_operation_id,replay_until,replay_encrypted) values(?,?,?,?,?,'ANDROID',28,0,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz,'BOOTSTRAP',?,?::timestamptz,'fictional-replay-envelope')",
                company,
                id,
                account,
                original.deviceId,
                session,
                compact(UUID.randomUUID()).repeat(2),
                compact(UUID.randomUUID()).repeat(2),
                at.toString(),
                at.toString(),
                at.plusSeconds(600).toString(),
                at.plusSeconds(300).toString(),
                UUID.randomUUID(),
                at.plusSeconds(30).toString(),
            )
            return id
        }

        fun keyProvider(
            action: (String, String, String, Instant) -> Result<CollaborationMatrixDeviceKeys?> =
                { user, device, _, deadline ->
                    val value = requireNotNull(retainedKeys)
                    assertEquals(value.userId, user)
                    assertEquals(value.deviceId, device)
                    assertTrue(deadline > Instant.now())
                    assertTrue(deadline <= Instant.now().plusSeconds(60))
                    Result.Success(value.device)
                }
        ): CollaborationMatrixKeyRepository =
            object :
                CollaborationMatrixKeyRepository by DisabledCollaborationMatrixKeyRepository() {
                override fun ownDeviceKeys(
                    userId: String,
                    deviceId: String,
                    token: String,
                    deadline: Instant,
                ): Result<CollaborationMatrixDeviceKeys?> =
                    action(userId, deviceId, token, deadline)
            }

        fun originalSnapshot(): Map<String, List<Map<String, Any?>>> =
            listOf(
                    "collaboration_devices",
                    "collaboration_provider_sessions",
                    "collaboration_gateway_sessions",
                    "collaboration_device_key_publications",
                )
                .associateWith {
                    owner.queryForList("select * from $it where account_id=? order by 1,2", account)
                }

        fun completion(original: CollaborationLegacyDevicePosition) =
            success(transactions.run(actor) { completions.find(runtimeOwner(original)) })

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
                    if (legacy == null) null else fingerprint(),
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

        fun registrationCommand(
            original: CollaborationLegacyDevicePosition,
            operation: UUID,
            nativeVersion: Long = 0,
            completions: CollaborationLegacyRuntimeCompletionRepository = this.completions,
            operations: OperationRepository = this.operations,
            clock: Clock = Clock.systemUTC(),
        ): Result<MutationReceipt> =
            RegisterNativeCollaborationSpaceDevice(
                    registry,
                    provisioning,
                    legacyParents,
                    completions,
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
                .execute(
                    actor,
                    session,
                    nativeVersion,
                    space,
                    client,
                    operation,
                    CollaborationPlatform.ANDROID,
                    "Fictional",
                    CollaborationSpaceDeviceOrigin.LEGACY,
                    original,
                    fingerprint(),
                )

        fun freezeCommand(
            id: UUID,
            operation: UUID,
            nativeVersion: Long = 0,
            completions: CollaborationLegacyRuntimeCompletionRepository = this.completions,
            operations: OperationRepository = this.operations,
            clock: Clock = Clock.systemUTC(),
        ): Result<MutationReceipt> =
            FreezeNativeCollaborationLegacyRuntime(
                    registry,
                    legacyParents,
                    selections,
                    runtimes,
                    provisioning,
                    completions,
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
                .execute(
                    actor,
                    session,
                    nativeVersion,
                    space,
                    client,
                    operation,
                    id,
                    0,
                    fingerprint(),
                )

        fun drainCommand(
            id: UUID,
            nativeVersion: Long = 0,
            completions: CollaborationLegacyRuntimeCompletionRepository = this.completions,
        ): Result<CollaborationLegacyRuntimeDrainStatus> =
            GetNativeCollaborationLegacyRuntimeDrain(
                    registry,
                    legacyParents,
                    selections,
                    runtimes,
                    provisioning,
                    completions,
                    spaces,
                    sessions,
                    identities,
                    transactions,
                    CollaborationSpacePolicy(true, 28, 28),
                    CollaborationMatrixPolicy(true, server),
                    IdentitySecurityPolicy(),
                    Clock.systemUTC(),
                )
                .execute(actor, session, nativeVersion, space, client, id, 0, fingerprint())

        fun retryCommand(
            id: UUID,
            operation: UUID,
            nativeVersion: Long = 0,
            revision: Long = 0,
        ): Result<MutationReceipt> =
            RetryNativeCollaborationSpaceProvisioning(
                    registry,
                    provisioning,
                    legacyParents,
                    completions,
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
                .execute(actor, session, nativeVersion, space, client, operation, id, 0, revision)

        fun receiptCount(): Int =
            requireNotNull(
                owner.queryForObject(
                    "select count(*) from operation_receipts where actor_id=?",
                    Int::class.java,
                    account,
                )
            )

        fun register(retained: CollaborationLegacyDevicePosition? = null): UUID {
            select()
            return success(
                    RegisterNativeCollaborationSpaceDevice(
                            registry,
                            provisioning,
                            legacyParents,
                            completions,
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
                            if (retained == null) null else fingerprint(),
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
            keys: CollaborationMatrixKeyRepository = keyProvider(),
            completions: CollaborationLegacyRuntimeCompletionRepository = this.completions,
        ): Result<Unit> =
            AdvanceCollaborationSpaceProvisioning(
                    registry,
                    provisioning,
                    credentials,
                    matrix,
                    keys,
                    legacyParents,
                    runtimes,
                    gateways,
                    completions,
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
                success(
                        ClaimCollaborationSpaceProvisioning(
                                provisioning,
                                CollaborationSpacePolicy(true, 28, 28),
                                CollaborationMatrixPolicy(true, server),
                            )
                            .execute(UUID.randomUUID(), 1)
                    )
                    .single()
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
                assertEquals(expected.legacy, request.retainedDevice)
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
