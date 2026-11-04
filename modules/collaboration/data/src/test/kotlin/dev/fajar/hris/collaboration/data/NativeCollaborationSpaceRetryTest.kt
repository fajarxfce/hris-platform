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
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
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
class NativeCollaborationSpaceRetryTest {
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
            "create role space_retry_command_fixture login password 'isolated-fixture-only'"
        )
        owner.execute("grant usage on schema public to space_retry_command_fixture")
        owner.execute("grant select on all tables in schema public to space_retry_command_fixture")
        owner.execute(
            "grant update on accounts,native_sessions,collaboration_space_devices,collaboration_space_provisioning to space_retry_command_fixture"
        )
        owner.execute(
            "grant insert on collaboration_space_provisioning,collaboration_space_identities,collaboration_space_devices,collaboration_space_device_sessions,operation_receipts,audit_entries,platform_audit_metadata,outbox_events to space_retry_command_fixture"
        )
        runtime =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "space_retry_command_fixture",
                "isolated-fixture-only",
            )
        owner.execute("create role space_retry_worker login password 'isolated-fixture-only'")
        owner.execute("grant usage on schema public to space_retry_worker")
        owner.execute("grant hris_worker_capability to space_retry_worker")
        owner.execute(
            "grant select,update on collaboration_space_provisioning to space_retry_worker"
        )
        workerQueue =
            StoredCollaborationSpaceProvisioningRepository(
                PostgresCollaborationSpaceProvisioningDataSource(
                    DSL.using(
                        DriverManagerDataSource(
                            postgres.jdbcUrl,
                            "space_retry_worker",
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
    fun freshNativeRetryKeepsOneHeadAndReceiptWhileReplayCannotRefreshProof() {
        val f = Fixture(verifiedMfa = true)
        val initial = f.seed()
        val device = initial.request.deviceId
        assertTrue(f.cancel(device))
        val ended = f.work(device)
        owner.update(
            "update native_sessions set version=version+1,rotated_at=clock_timestamp(),access_expires_at=clock_timestamp()+interval '20 seconds' where id=?",
            f.session,
        )
        val operation = UUID.randomUUID()
        failure(f.retry(device, operation), "native_session_invalid")
        val receipt = success(f.retry(device, operation, version = 1))
        assertEquals(MutationReceipt(device, 1), receipt)
        val queued = f.work(device)
        assertEquals(initial.createdAt, queued.createdAt)
        assertEquals(ended.lastOutcome, queued.lastOutcome)
        assertEquals(f.actor.mfaVerifiedAt, queued.request.mfaVerifiedAt)
        assertNotNull(queued.request.mfaVerifiedAt)
        assertEquals(1, queued.request.nativeSessionVersion)
        assertEquals(1, queued.request.revision)
        assertTrue(queued.request.expiresAt <= queued.request.requestedAt.plusSeconds(20))
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, queued.state)
        assertNull(queued.lease)
        f.retryCounts(1)
        assertEquals(
            receipt.copy(replayed = true),
            success(f.retry(device, operation, version = 1)),
        )
        failure(f.retry(device, version = 1), "collaboration_provisioning_changed")
        failure(f.retry(device, operation, version = 1, revision = 1), "operation_payload_mismatch")
        failure(
            f.retry(device, operation, version = 1, request = f.actor.copy(companyId = f.company)),
            "platform_scope_required",
        )
        failure(
            f.retry(device, operation, version = 1, build = client.copy(build = 27)),
            "client_update_required",
        )
        owner.update(
            "update native_sessions set version=version+1,rotated_at=clock_timestamp() where id=?",
            f.session,
        )
        assertEquals(
            receipt.copy(replayed = true),
            success(f.retry(device, operation, version = 2)),
        )
        assertEquals(queued, f.work(device))
        owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.account,
        )
        failure(f.retry(device, operation, version = 2), "collaboration_access_denied")
        f.retryCounts(1)

        val recent = Fixture()
        val waiting = recent.seed(recent = true)
        failure(recent.retry(waiting.request.deviceId), "collaboration_provisioning_changed")
        assertTrue(recent.cancel(waiting.request.deviceId))
        failure(recent.retry(waiting.request.deviceId), "collaboration_provisioning_retry_too_soon")
        recent.retryCounts(0)
        failure(
            recent.retry(waiting.request.deviceId, revision = 999999),
            "invalid_collaboration_provisioning_version",
        )
        failure(
            recent.retry(waiting.request.deviceId, deviceVersion = -1),
            "invalid_collaboration_provisioning_version",
        )
        failure(
            recent.retry(
                waiting.request.deviceId,
                request = recent.actor.copy(accountId = UUID.randomUUID()),
            ),
            "session_revoked",
        )
    }

    @Test
    fun competingRetriesHaveOneWinnerAndOldWorkerLeaseCannotPublish() {
        val f = Fixture()
        val initial = f.seed()
        val device = initial.request.deviceId
        val lease =
            requireNotNull(success(workerQueue.claim(UUID.randomUUID(), 1, 60)).single().lease)
        assertEquals(device, lease.request.deviceId)
        assertTrue(
            success(
                workerQueue.finish(
                    lease,
                    CollaborationSpaceProvisioningFailure.PROVIDER_UNAVAILABLE,
                )
            )
        )
        val failed = f.work(device)
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                (1..2)
                    .map {
                        pool.submit<Result<MutationReceipt>> {
                            barrier.await(5, TimeUnit.SECONDS)
                            f.retry(device)
                        }
                    }
                    .map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            failure(results.first { it is Result.Failed }, "collaboration_provisioning_changed")
            f.retryCounts(1)
            val head = f.work(device)
            assertEquals(1, head.request.revision)
            assertEquals(failed.lastOutcome, head.lastOutcome)
            assertEquals(initial.createdAt, head.createdAt)
            assertFalse(success(workerQueue.finish(lease)))
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun expiredPendingCancellationRetryReceiptAndJournalRollBackTogetherAtEveryBoundary() {
        for (stage in listOf("cancel", "retry", "receipt", "journal")) for (mode in
            listOf("failure", "cancel", "interrupt")) {
            val f = Fixture()
            val queued = f.seed(expiresAt = Instant.now().plusSeconds(1))
            val device = queued.request.deviceId
            val oldLease =
                if (stage == "cancel")
                    requireNotNull(
                        success(workerQueue.claim(UUID.randomUUID(), 1, 60)).single().lease
                    )
                else null
            if (oldLease != null) assertEquals(device, oldLease.request.deviceId)
            val initial = f.work(device)
            Thread.sleep(1100)
            val operation = UUID.randomUUID()
            fun injected(): Result<Unit> {
                if (mode == "cancel") throw CancellationException("fixture cancellation")
                if (mode == "interrupt") throw InterruptedException("fixture interruption")
                return Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_write_failed"))
            }
            val queue =
                object : CollaborationSpaceProvisioningRepository by f.provisioning {
                    override fun cancel(
                        spaceId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                        expectedRevision: Long,
                    ): Result<Boolean> =
                        f.provisioning
                            .cancel(spaceId, accountId, deviceId, expectedRevision)
                            .flatMap { value ->
                                if (stage == "cancel") injected().map { value }
                                else Result.Success(value)
                            }

                    override fun retry(
                        request: CollaborationSpaceProvisioningRequest,
                        expectedRevision: Long,
                    ): Result<Unit> =
                        f.provisioning.retry(request, expectedRevision).flatMap {
                            if (stage == "retry") injected() else Result.Success(Unit)
                        }
                }
            val operations =
                object : OperationRepository by f.operations {
                    override fun record(
                        actor: Actor,
                        key: OperationKey,
                        receipt: MutationReceipt,
                    ): Result<Unit> =
                        f.operations.record(actor, key, receipt).flatMap {
                            if (stage == "receipt") injected() else Result.Success(Unit)
                        }
                }
            val journal = hookJournal { actor, record ->
                f.journal.record(actor, record).flatMap {
                    if (stage == "journal") injected() else Result.Success(Unit)
                }
            }
            fun run() =
                f.retry(
                    device,
                    operation,
                    provisioning = queue,
                    operations = operations,
                    journal = journal,
                )
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { run() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { run() }
                else -> failure(run(), "fixture_write_failed")
            }
            assertEquals(initial, f.work(device))
            f.retryCounts(0)
            assertEquals(MutationReceipt(device, 1), success(f.retry(device, operation)))
            val fresh = f.work(device)
            if (oldLease != null) assertFalse(success(workerQueue.finish(oldLease)))
            assertEquals(CollaborationSpaceProvisioningState.CANCELLED, fresh.lastOutcome?.state)
            assertEquals(0, fresh.lastOutcome?.revision)
            assertEquals(1, fresh.request.revision)
            f.retryCounts(1)
        }
    }

    @Test
    fun finalAdmissionRejectsLateSecuritySourceAndClockChangesWithoutReplacingTheHead() {
        for (change in listOf("offboard", "mfa", "legacy", "backward", "budget", "interrupt")) {
            val f = Fixture()
            val legacy = if (change == "legacy") f.activeLegacy() else null
            val initial = f.seed(legacy = legacy)
            val device = initial.request.deviceId
            assertTrue(f.cancel(device))
            val ended = f.work(device)
            val at = Instant.now()
            val clock = MutableClock(at)
            val journal = hookJournal { actor, record ->
                f.journal.record(actor, record).map {
                    when (change) {
                        "offboard" ->
                            owner.update(
                                "update company_memberships set active=false,version=version+1 where account_id=?",
                                f.account,
                            )
                        "mfa" ->
                            owner.update(
                                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'identity.manage')",
                                f.company,
                                f.account,
                            )
                        "legacy" ->
                            owner.update(
                                "update collaboration_devices set state='REVOKED',version=2,revoked_at=clock_timestamp(),updated_at=clock_timestamp() where company_id=? and id=?",
                                legacy!!.companyId,
                                legacy.deviceId,
                            )
                        "backward" -> clock.at = at.minusSeconds(1)
                        "budget" -> clock.at = at.plusSeconds(30)
                        "interrupt" -> Thread.currentThread().interrupt()
                    }
                    Unit
                }
            }
            if (change == "interrupt") {
                try {
                    assertThrows(InterruptedException::class.java) {
                        f.retry(device, journal = journal, clock = clock)
                    }
                    assertTrue(Thread.currentThread().isInterrupted)
                } finally {
                    Thread.interrupted()
                }
            } else
                failure(
                    f.retry(device, journal = journal, clock = clock),
                    when (change) {
                        "offboard" -> "collaboration_access_denied"
                        "mfa" -> "mfa_setup_required"
                        "legacy" -> "collaboration_device_migration_required"
                        else -> "collaboration_admission_expired"
                    },
                )
            assertEquals(ended, f.work(device))
            f.retryCounts(0)
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
        val completions =
            StoredCollaborationLegacyRuntimeCompletionRepository(
                PostgresCollaborationLegacyRuntimeCompletionDataSource(sql)
            )
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

        fun retry(
            device: UUID,
            operation: UUID = UUID.randomUUID(),
            request: Actor = actor,
            version: Long = 0,
            deviceVersion: Long = 0,
            revision: Long = 0,
            build: ClientVersion? = client,
            requestedSpace: UUID = space,
            requestedServer: String = server,
            provisioning: CollaborationSpaceProvisioningRepository = this.provisioning,
            operations: OperationRepository = this.operations,
            journal: ChangeJournalRepository = this.journal,
            clock: Clock = Clock.systemUTC(),
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
                    CollaborationMatrixPolicy(true, requestedServer),
                    IdentitySecurityPolicy(),
                    clock,
                )
                .execute(
                    request,
                    session,
                    version,
                    requestedSpace,
                    build,
                    operation,
                    device,
                    deviceVersion,
                    revision,
                )

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

        fun retryCounts(expected: Int) {
            for ((table, column) in
                listOf(
                    "operation_receipts" to "operation",
                    "audit_entries" to "action",
                    "platform_audit_metadata" to "action",
                    "outbox_events" to "event_type",
                )) assertEquals(
                expected,
                owner.queryForObject(
                    "select count(*) from $table where actor_id=? and $column=?",
                    Int::class.java,
                    account,
                    if (table == "operation_receipts") "collaboration.space_provisioning_retry"
                    else "collaboration.space_provisioning_retried",
                ),
            )
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
