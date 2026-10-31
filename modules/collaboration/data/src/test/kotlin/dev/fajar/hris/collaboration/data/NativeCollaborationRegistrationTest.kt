package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.data.datasources.PostgresCompanyClientPolicyDataSource
import dev.fajar.hris.administration.data.repositories.StoredCompanyClientPolicyRepository
import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationGatewaySessionDataSource
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationProviderSessionDataSource
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationProvisioningDataSource
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationRegistryDataSource
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationGatewaySessionRepository
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationProviderSessionRepository
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationProvisioningRepository
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationRegistryRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixDeviceId
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.collaboration.domain.repositories.CollaborationGatewaySessionRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRepository
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.core.database.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.jobs.data.datasources.PostgresJobDataSource
import dev.fajar.hris.jobs.data.repositories.PostgresJobRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.organization.data.datasources.PostgresCompanyDataSource
import dev.fajar.hris.organization.data.repositories.StoredCompanyRepository
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper

@Testcontainers
class NativeCollaborationRegistrationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = org.testcontainers.postgresql.PostgreSQLContainer("postgres:18.6-alpine")
        private lateinit var source: DriverManagerDataSource
        private lateinit var sql: JdbcTemplate
        private lateinit var runtime: DriverManagerDataSource

        @BeforeAll
        @JvmStatic
        fun prepare() {
            source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            sql = JdbcTemplate(source)
            Flyway.configure().dataSource(source).load().migrate()
            sql.execute(
                "create role collaboration_fixture login password 'collaboration-fixture-only'"
            )
            sql.execute("grant usage on schema public to collaboration_fixture")
            sql.execute(
                "grant select,insert,update,delete on all tables in schema public to collaboration_fixture"
            )
            sql.execute(
                "grant usage,select on all sequences in schema public to collaboration_fixture"
            )
            runtime =
                DriverManagerDataSource(
                    postgres.jdbcUrl,
                    "collaboration_fixture",
                    "collaboration-fixture-only",
                )
        }
    }

    @Test
    fun competingParentsCannotExceedFiveSlots() {
        val f = Fixture()
        repeat(4) { assertInstanceOf(Result.Success::class.java, f.register(f.parent())) }
        val candidates = listOf(f.parent(), f.parent())
        val outcomes = race(candidates.map { parent -> { f.register(parent) } })
        assertEquals(1, outcomes.count { it is Result.Success })
        assertEquals(
            listOf("collaboration_device_limit"),
            outcomes.filterIsInstance<Result.Failed>().map { it.failure.code },
        )
        assertEquals(5, f.rows("collaboration_devices"))
        assertEquals(5, f.rows("background_jobs"))
        assertEquals(5, f.rows("collaboration_device_provisioning"))
        assertEquals(5, f.rows("operation_receipts"))
        assertEquals(5, f.rows("audit_entries"))
        assertEquals(5, f.rows("outbox_events"))
    }

    @Test
    fun concurrentLostResponseReplaySurvivesRefreshWithoutDuplicatingSideEffects() {
        val f = Fixture()
        val parent = f.parent()
        val operation = UUID.randomUUID()
        val receipts = race(List(2) { { f.register(parent, operation) } }).map { success(it) }
        assertEquals(receipts[0].id, receipts[1].id)
        assertEquals(1, receipts.count { it.replayed })
        sql.update("update native_sessions set version=version+1 where id=?", parent)
        val replay = success(f.register(parent, operation, version = 1))
        assertEquals(receipts[0].id, replay.id)
        assertTrue(replay.replayed)
        assertEquals(
            "operation_payload_mismatch",
            failed(f.register(parent, operation, version = 1, label = "Changed name")),
        )
        assertEquals(1, f.rows("collaboration_devices"))
        assertEquals(1, f.rows("operation_receipts"))
        assertEquals(1, f.rows("audit_entries"))
        assertEquals(1, f.rows("outbox_events"))
        assertEquals(1, f.rows("background_jobs"))
        assertEquals(1, f.rows("collaboration_device_provisioning"))
        // Revocation is checked even when the exact successful receipt is present.
        sql.update(
            "update collaboration_devices set state='REVOKED',version=version+1,updated_at=now(),revoked_at=now() where id=?",
            replay.id,
        )
        assertEquals(
            "collaboration_access_denied",
            failed(f.register(parent, operation, version = 1)),
        )
    }

    @Test
    fun oneParentInTwoCompaniesUsesOneSlotButKeepsSeparateChatIdentities() {
        val f = Fixture()
        val parents = List(5) { f.parent() }
        val first = success(f.register(parents[0]))
        parents.drop(1).forEach { success(f.register(it)) }
        val secondCompany = f.company()
        val second =
            success(f.register(parents[0], actor = f.actor.copy(companyId = secondCompany)))
        assertNotEquals(first.id, second.id)
        assertEquals(
            Result.Success(5),
            f.transactions.run(f.actor) {
                f.registry.countActiveParentSessions(f.account, f.clock.instant())
            },
        )
        assertEquals(
            Result.Success(4),
            f.transactions.run(f.actor) {
                f.registry.countActiveParentSessions(f.account, f.clock.instant(), parents[0])
            },
        )
        assertEquals("collaboration_device_limit", failed(f.register(f.parent())))
        sql.update("update native_sessions set revoked_at=now() where id=?", parents[1])
        assertInstanceOf(Result.Success::class.java, f.register(f.parent()))
    }

    @Test
    fun expiredAccessStillConsumesARefreshableParentSlot() {
        val f = Fixture()
        val parents = List(5) { f.parent() }
        parents.forEach { success(f.register(it)) }
        sql.update(
            "update native_sessions set access_expires_at=now()-interval '1 second' where id=?",
            parents[0],
        )
        assertEquals("collaboration_device_limit", failed(f.register(f.parent())))
        assertEquals("native_session_invalid", failed(f.register(parents[0])))
        assertEquals(5, f.rows("operation_receipts"))
    }

    @Test
    fun staleForeignAndRevokedAuthorityCannotRegisterOrReplay() {
        val f = Fixture()
        val parent = f.parent()
        assertEquals("native_session_invalid", failed(f.register(parent, version = 1)))
        assertEquals("native_session_invalid", failed(f.register(Fixture().parent())))
        assertEquals(
            "invalid_collaboration_device",
            failed(
                f.command()
                    .execute(
                        f.actor,
                        parent,
                        0,
                        UUID.randomUUID(),
                        CollaborationPlatform.WEB,
                        "Browser",
                        ClientVersion(ClientPlatform.WEB, 1),
                    )
            ),
        )
        assertEquals(
            "access_denied",
            failed(f.register(parent, actor = f.actor.copy(permissions = emptySet()))),
        )
        val operation = UUID.randomUUID()
        success(f.register(parent, operation))
        sql.update("delete from membership_permissions where account_id=?", f.account)
        assertInstanceOf(Result.Failed::class.java, f.register(parent, operation))
        assertEquals(1, f.rows("collaboration_devices"))
        assertEquals(1, f.rows("operation_receipts"))
    }

    @Test
    fun revocationCommittedWhileRegistrationWaitsIsObserved() {
        val f = Fixture()
        val parent = f.parent()
        val waiting = CountDownLatch(1)
        val identities =
            object : IdentityRepository by f.identities {
                override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> {
                    waiting.countDown()
                    return f.identities.lockAccount(accountId, shared)
                }
            }
        val executor = Executors.newSingleThreadExecutor()
        var future: Future<Result<MutationReceipt>>? = null
        try {
            TransactionTemplate(JdbcTransactionManager(source)).executeWithoutResult {
                sql.queryForObject(
                    "select id from accounts where id=? for update",
                    UUID::class.java,
                    f.account,
                )
                future =
                    executor.submit<Result<MutationReceipt>> {
                        f.register(parent, command = f.command(identities = identities))
                    }
                assertTrue(waiting.await(5, TimeUnit.SECONDS))
                sql.update("update native_sessions set revoked_at=now() where id=?", parent)
            }
            assertEquals(
                "native_session_invalid",
                failed(requireNotNull(future).get(10, TimeUnit.SECONDS)),
            )
            assertEquals(0, f.rows("collaboration_devices"))
            assertEquals(0, f.rows("operation_receipts"))
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun expiryAfterAuditRollsBackDeviceBindingReceiptAndOutboxTogether() {
        val f = Fixture()
        val parent = f.parent()
        val journal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
                    return f.journal.record(actor, change).also {
                        f.clock.at = f.clock.at.plusSeconds(3600)
                    }
                }
            }
        assertEquals(
            "native_session_invalid",
            failed(f.register(parent, command = f.command(journal = journal))),
        )
        for (table in
            listOf(
                "collaboration_devices",
                "collaboration_device_sessions",
                "collaboration_device_provisioning",
                "background_jobs",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )) assertEquals(0, f.rows(table), table)
    }

    @Test
    fun queueCapacityRollsBackRegistrationAndLegacyPendingIdentityEnqueuesOnlyOnce() {
        val full = Fixture()
        assertEquals(
            Result.Success(Unit),
            full.transactions.run(full.actor) {
                repeat(100) {
                    val job =
                        JobRequest(
                            UUID.randomUUID(),
                            requireNotNull(full.actor.companyId),
                            full.account,
                            JobKind.REPORT_EXPORT,
                            UUID.randomUUID(),
                            emptyMap(),
                            full.actor.authenticatedAt,
                            0,
                            full.actor.correlationId,
                            full.clock.at,
                            1,
                        )
                    val created = full.jobs.create(job)
                    if (created is Result.Failed) return@run created
                }
                Result.Success(Unit)
            },
        )
        assertEquals("job_queue_full", failed(full.register(full.parent())))
        assertEquals(100, full.rows("background_jobs"))
        for (table in
            listOf(
                "collaboration_devices",
                "collaboration_device_sessions",
                "collaboration_device_provisioning",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )) assertEquals(0, full.rows(table), table)

        val legacy = Fixture()
        val parent = legacy.parent()
        val device = UUID.randomUUID()
        assertEquals(
            Result.Success(Unit),
            legacy.transactions.run(legacy.actor) {
                legacy.registry.register(
                    CollaborationRegistration(
                        device,
                        requireNotNull(legacy.actor.companyId),
                        legacy.account,
                        parent,
                        CollaborationPlatform.ANDROID,
                        "Retained device",
                        legacy.clock.at,
                    )
                )
            },
        )
        val operation = UUID.randomUUID()
        assertEquals(
            "invalid_collaboration_device",
            failed(
                legacy.register(
                    parent,
                    platform = CollaborationPlatform.IOS,
                    client = ClientVersion(ClientPlatform.IOS, 1),
                )
            ),
        )
        assertEquals(0, legacy.rows("background_jobs"))
        val result = success(legacy.register(parent, operation))
        assertEquals(device, result.id)
        assertEquals(device, success(legacy.register(parent, operation)).id)
        assertEquals(1, legacy.rows("collaboration_devices"))
        assertEquals(1, legacy.rows("background_jobs"))
        assertEquals(1, legacy.rows("collaboration_device_provisioning"))
        assertEquals(1, legacy.rows("audit_entries"))
        legacy.activate(device)
        assertEquals(
            "invalid_collaboration_device",
            failed(legacy.acquire(parent, client = ClientVersion(ClientPlatform.IOS, 1))),
        )
    }

    @Test
    fun durableJobBindingSurvivesRestoreAndRejectsCrossScopeOrUnfinishedReplacement() {
        val f = Fixture()
        val parent = f.parent()
        val device = success(f.register(parent)).id
        val first = f.claim(device)
        success(
            f.abort()
                .execute(
                    first,
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable"),
                )
        )
        f.clock.at = f.clock.at.plusSeconds(61)
        val retried =
            success(
                f.retry()
                    .execute(
                        f.actor,
                        parent,
                        0,
                        UUID.randomUUID(),
                        device,
                        0,
                        0,
                        ClientVersion(ClientPlatform.ANDROID, 1),
                    )
            )
        val company = requireNotNull(f.actor.companyId)
        val pointer =
            requireNotNull(
                success(
                    f.transactions.run(f.actor) { f.provisioning.find(company, f.account, device) }
                )
            )
        val job =
            requireNotNull(
                success(f.transactions.run(f.actor) { f.jobs.find(company, pointer.jobId) })
            )
        assertEquals(JobKind.COLLABORATION_PROVISION, job.request.kind)
        assertEquals(device.toString(), job.request.values["deviceId"])
        assertEquals(parent.toString(), job.request.values["nativeSessionId"])
        assertEquals("ANDROID", job.request.values["platform"])
        assertEquals("1", job.request.values["clientBuild"])
        assertEquals(0, job.attempts)
        assertEquals(JobStatus.QUEUED, job.status)
        assertEquals(1L, pointer.version)
        assertEquals(retried.id, pointer.jobId)
        assertNotEquals(first.job.request.id, pointer.jobId)

        val restored = "provisioning_" + UUID.randomUUID().toString().replace("-", "")
        fun command(vararg args: String) {
            val result = postgres.execInContainer(*args)
            assertEquals(0, result.exitCode, result.stderr)
        }
        command(
            "pg_dump",
            "-U",
            postgres.username,
            "-d",
            postgres.databaseName,
            "-Fc",
            "-f",
            "/tmp/provisioning.dump",
        )
        command("createdb", "-U", postgres.username, restored)
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            restored,
            "--exit-on-error",
            "/tmp/provisioning.dump",
        )
        val url = postgres.jdbcUrl.substringBeforeLast('/') + "/" + restored
        val restoredSource =
            DriverManagerDataSource(url, "collaboration_fixture", "collaboration-fixture-only")
        val tx =
            PostgresTransactionRunner(
                JdbcTransactionManager(restoredSource),
                JdbcTemplate(restoredSource),
            )
        val repository =
            StoredCollaborationProvisioningRepository(
                PostgresCollaborationProvisioningDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(restoredSource), SQLDialect.POSTGRES)
                )
            )
        assertEquals(
            pointer,
            success(tx.run(f.actor) { repository.find(company, f.account, device) }),
        )
        assertEquals(
            Result.Success(null),
            tx.run(f.actor.copy(accountId = UUID.randomUUID())) {
                repository.find(company, f.account, device)
            },
        )
        assertEquals(
            Result.Success(null),
            tx.run(f.actor.copy(companyId = UUID.randomUUID())) {
                repository.find(company, f.account, device)
            },
        )
        val restoredSql =
            JdbcTemplate(DriverManagerDataSource(url, postgres.username, postgres.password))
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            restoredSql.update(
                "delete from collaboration_device_provisioning where device_id=?",
                device,
            )
        }
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            restoredSql.update(
                "update collaboration_device_provisioning set version=2,job_id=? where device_id=?",
                UUID.randomUUID(),
                device,
            )
        }
        assertEquals(
            "FAILED",
            restoredSql.queryForObject(
                "select status from background_jobs where id=?",
                String::class.java,
                first.job.request.id,
            ),
        )
        restoredSql.update("delete from native_sessions where id=?", parent)
        assertEquals(
            pointer,
            success(tx.run(f.actor) { repository.find(company, f.account, device) }),
        )
    }

    @Test
    fun pendingDeviceHasNoGrantAndActiveGrantStopsAtScheduledPolicyBoundary() {
        val f = Fixture()
        val parent = f.parent()
        val receipt = success(f.register(parent))
        assertEquals("collaboration_access_denied", failed(f.acquire(parent)))
        f.activate(receipt.id)
        val boundary = f.clock.at.plusSeconds(25)
        f.policy(ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)), boundary)
        assertEquals(boundary, success(f.acquire(parent)).expiresAt)
        f.clock.at = boundary
        assertEquals("company_module_disabled", failed(f.acquire(parent)))
        assertEquals("company_module_disabled", failed(f.register(parent)))
    }

    @Test
    fun scheduledPolicyCrossedDuringLookupCannotIssueAStaleGrant() {
        val f = Fixture()
        val parent = f.parent()
        f.activate(success(f.register(parent)).id)
        val boundary = f.clock.at.plusSeconds(10)
        f.policy(ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)), boundary)
        val delayed =
            object : CompanyClientPolicyRepository by f.policies {
                override fun effective(
                    companyId: UUID,
                    at: Instant,
                ): Result<EffectiveClientPolicy> =
                    f.policies.effective(companyId, at).also { f.clock.at = boundary }
            }
        assertEquals("collaboration_admission_changed", failed(f.acquire(parent, delayed)))
    }

    @Test
    fun policyActivationAfterWritesRollsBackAndMinimumBuildDoesNotAcceptBrowserMetadata() {
        val f = Fixture()
        val parent = f.parent()
        f.policy(ClientPolicy(minimumBuilds = MinimumClientBuilds(android = 10)))
        assertEquals("client_update_required", failed(f.register(parent)))
        assertEquals(
            "invalid_collaboration_device",
            failed(f.register(parent, client = ClientVersion(ClientPlatform.WEB, 100))),
        )
        val operation = UUID.randomUUID()
        success(f.register(parent, operation, client = ClientVersion(ClientPlatform.ANDROID, 10)))
        val second = Fixture()
        val boundary = second.clock.at.plusSeconds(10)
        second.policy(ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)), boundary)
        val journal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    second.journal.record(actor, change).also { second.clock.at = boundary }
            }
        assertEquals(
            "company_module_disabled",
            failed(second.register(second.parent(), command = second.command(journal = journal))),
        )
        assertEquals(0, second.rows("collaboration_devices"))
        assertEquals(0, second.rows("audit_entries"))
    }

    @Test
    fun provisioningPublishesEncryptedCredentialDeviceAndJobTogetherAndRetryCannotRepublish() {
        val f = Fixture()
        val parent = f.parent()
        val device = success(f.register(parent)).id
        val lease = f.claim(device)
        val matrix = RecordingMatrix(f.clock)
        assertEquals(JobStep(1, true), success(f.advance(matrix).execute(lease)))
        assertEquals("ACTIVE", f.state(device))
        val job = f.job(lease)
        assertEquals(JobStatus.SUCCEEDED, job.status)
        assertEquals(1, job.completedItems)
        assertEquals(device.toString(), job.checkpoint["deviceId"])
        val credential = requireNotNull(f.credential(device))
        assertEquals(0L, credential.generation)
        assertEquals(
            "@${collaborationMatrixLocalpart(requireNotNull(f.actor.companyId), f.account)}:proof.invalid",
            credential.matrixUserId,
        )
        assertEquals(collaborationMatrixDeviceId(device), credential.matrixDeviceId)
        assertFalse(
            requireNotNull(
                    sql.queryForObject(
                        "select token_encrypted from collaboration_provider_sessions where device_id=?",
                        String::class.java,
                        device,
                    )
                )
                .contains(credential.accessToken)
        )
        assertEquals(2, f.rows("audit_entries"))
        assertEquals(2, f.rows("outbox_events"))
        assertEquals("job_lease_lost", failed(f.advance(matrix).execute(lease)))
        assertEquals(1, matrix.logins.get())
        assertEquals(0, matrix.logouts.get())
        assertInstanceOf(Result.Success::class.java, f.acquire(parent))
    }

    @Test
    fun providerCallReleasesSqlGuardsAndPermissionMfaAndPolicyChangesBlockPublication() {
        for (mode in 0..2) {
            val f = Fixture()
            val device = success(f.register(f.parent())).id
            val lease = f.claim(device)
            val matrix = RecordingMatrix(f.clock)
            matrix.onLogin = { request ->
                // These independent transactions would time out if provider I/O held access guards.
                when (mode) {
                    0 ->
                        sql.update(
                            "delete from membership_permissions where account_id=?",
                            f.account,
                        )
                    1 ->
                        sql.update(
                            "update accounts set mfa_secret_encrypted='fictional-configured-secret' where id=?",
                            f.account,
                        )
                    else ->
                        f.policy(ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)))
                }
                matrix.issue(request)
            }
            val result = f.advance(matrix).execute(lease)
            assertInstanceOf(Result.Failed::class.java, result)
            if (mode == 1) assertEquals("mfa_required", failed(result))
            if (mode == 2) assertEquals("company_module_disabled", failed(result))
            assertEquals("PROVISIONING", f.state(device))
            assertNull(f.credential(device))
            assertEquals(JobStatus.RUNNING, f.job(lease).status)
            assertEquals(0, matrix.logouts.get())
            assertEquals(
                Result.Success(Unit),
                f.abort().execute(lease, (result as Result.Failed).failure),
            )
            assertEquals(JobStatus.FAILED, f.job(lease).status)
        }
    }

    @Test
    fun scheduledPolicyBoundaryRetriesObservationButNeverRetriesAConfirmedDenial() {
        val f = Fixture()
        val device = success(f.register(f.parent())).id
        val lease = f.claim(device)
        val activation = f.clock.at.plusSeconds(1)
        f.policy(ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)), activation)
        val delayed =
            object : CompanyClientPolicyRepository by f.policies {
                override fun effective(
                    companyId: UUID,
                    at: Instant,
                ): Result<EffectiveClientPolicy> =
                    f.policies.effective(companyId, at).also { f.clock.at = activation }
            }
        val matrix = RecordingMatrix(f.clock)
        val stale = f.advance(matrix, policies = delayed).execute(lease)
        assertEquals("collaboration_admission_changed", failed(stale))
        assertEquals(FailureKind.UNAVAILABLE, (stale as Result.Failed).failure.kind)
        val denied = f.advance(matrix).execute(lease)
        assertEquals("company_module_disabled", failed(denied))
        assertNotEquals(FailureKind.UNAVAILABLE, (denied as Result.Failed).failure.kind)
        assertEquals(0, matrix.logins.get())
        assertNull(f.credential(device))
        assertEquals("PROVISIONING", f.state(device))
    }

    @Test
    fun nativeRefreshRejectsTheFirstCandidateAndBoundedRetryKeepsTheDeviceIdentity() {
        val f = Fixture()
        val parent = f.parent()
        val device = success(f.register(parent)).id
        val lease = f.claim(device)
        val matrix = RecordingMatrix(f.clock)
        matrix.onLogin = { request ->
            sql.update("update native_sessions set version=version+1 where id=?", parent)
            matrix.issue(request)
        }
        val result = f.advance(matrix).execute(lease)
        assertEquals("collaboration_admission_changed", failed(result))
        assertEquals(FailureKind.UNAVAILABLE, (result as Result.Failed).failure.kind)
        assertNull(f.credential(device))
        assertEquals("PROVISIONING", f.state(device))
        matrix.onLogin = matrix::issue
        assertEquals(JobStep(1, true), success(f.advance(matrix).execute(lease)))
        assertEquals(1, f.rows("collaboration_devices"))
        assertEquals(1, f.rows("background_jobs"))
        assertEquals(2, matrix.logins.get())
        assertEquals(0, matrix.logouts.get())
    }

    @Test
    fun lateWorkerCannotOverwriteOrLogOutTheWinningProviderSession() {
        val f = Fixture()
        val device = success(f.register(f.parent())).id
        val firstLease = f.claim(device)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = RecordingMatrix(f.clock)
        first.onLogin = { request ->
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            first.issue(request)
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending = executor.submit<Result<JobStep>> { f.advance(first).execute(firstLease) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            // Seed replacement ownership as if the first worker's lease had expired/recovered.
            val secondLease = f.claim(device)
            val second = RecordingMatrix(f.clock)
            assertEquals(JobStep(1, true), success(f.advance(second).execute(secondLease)))
            val winning = requireNotNull(f.credential(device)).accessToken
            release.countDown()
            assertEquals("job_lease_lost", failed(pending.get(5, TimeUnit.SECONDS)))
            assertEquals(winning, f.credential(device)?.accessToken)
            assertEquals("ACTIVE", f.state(device))
            assertEquals(1, f.rows("collaboration_provider_sessions"))
            assertEquals(2, f.rows("audit_entries"))
            assertEquals(0, first.logouts.get() + second.logouts.get())
        } finally {
            release.countDown()
            executor.shutdown()
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun postWriteExpiryRollsBackCredentialActivationProgressCompletionAndAudit() {
        val f = Fixture()
        val device = success(f.register(f.parent())).id
        val lease = f.claim(device)
        val matrix = RecordingMatrix(f.clock)
        val journal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    f.journal.record(actor, change).also {
                        f.clock.at = f.clock.at.plusSeconds(3600)
                    }
            }
        assertEquals(
            "collaboration_admission_changed",
            failed(f.advance(matrix, journal).execute(lease)),
        )
        assertNull(f.credential(device))
        assertEquals("PROVISIONING", f.state(device))
        assertEquals(JobStatus.RUNNING, f.job(lease).status)
        assertEquals(0, f.job(lease).completedItems)
        assertEquals(1, f.rows("audit_entries"))
        assertEquals(1, f.rows("outbox_events"))
        assertEquals(0, matrix.logouts.get())
    }

    @Test
    fun malformedOrWrongProviderResultsAndCancellationNeverActivateADevice() {
        for (mode in 0..2) {
            val f = Fixture()
            val device = success(f.register(f.parent())).id
            val lease = f.claim(device)
            val matrix = RecordingMatrix(f.clock)
            if (mode == 0) {
                val malformed =
                    lease.copy(
                        job = lease.job.copy(request = lease.job.request.copy(values = emptyMap()))
                    )
                assertEquals(
                    "invalid_collaboration_provisioning",
                    failed(f.advance(matrix).execute(malformed)),
                )
                assertEquals(0, matrix.logins.get())
            } else {
                matrix.onLogin = { request ->
                    if (mode == 2)
                        sql.update(
                            "update background_jobs set cancellation_requested=true,version=version+1 where id=?",
                            lease.job.request.id,
                        )
                    val issued = success(matrix.issue(request))
                    Result.Success(
                        CollaborationMatrixCredential(
                            if (mode == 1) "@unrelated:proof.invalid" else issued.userId,
                            issued.deviceId,
                            issued.accessToken,
                            issued.expiresAt,
                        )
                    )
                }
                val result = f.advance(matrix).execute(lease)
                assertEquals(
                    if (mode == 1) "collaboration_provider_invalid_response"
                    else "job_cancellation_requested",
                    failed(result),
                )
                if (mode == 2) {
                    assertEquals(
                        Result.Success(Unit),
                        f.abort().execute(lease, (result as Result.Failed).failure),
                    )
                    assertEquals(JobStatus.CANCELLED, f.job(lease).status)
                }
            }
            assertEquals("PROVISIONING", f.state(device))
            assertNull(f.credential(device))
            assertEquals(0, matrix.logouts.get())
        }
    }

    @Test
    fun nativeStatusIsOwnedLiveMetadataAndExplicitRetryKeepsIdentityAfterResponseLoss() {
        val f = Fixture()
        val parent = f.parent()
        val client = ClientVersion(ClientPlatform.ANDROID, 1)
        assertNull(success(f.status().execute(f.actor, parent, 0, client)))
        val device = success(f.register(parent)).id
        val queued = requireNotNull(success(f.status().execute(f.actor, parent, 0, client)))
        assertEquals(device, queued.id)
        assertEquals(JobStatus.QUEUED, queued.provisioning?.status)
        val old = f.claim(device)
        success(
            f.abort()
                .execute(
                    old,
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable"),
                )
        )
        val stopped = requireNotNull(success(f.status().execute(f.actor, parent, 0, client)))
        assertEquals(JobStatus.FAILED, stopped.provisioning?.status)
        assertNotNull(stopped.provisioning?.retryNotBefore)
        val operation = UUID.randomUUID()
        assertEquals(
            "collaboration_provisioning_retry_too_soon",
            failed(f.retry().execute(f.actor, parent, 0, operation, device, 0, 0, client)),
        )
        f.clock.at = requireNotNull(stopped.provisioning?.retryNotBefore)
        val receipt =
            success(f.retry().execute(f.actor, parent, 0, operation, device, 0, 0, client))
        assertNotEquals(old.job.request.id, receipt.id)
        assertEquals(
            receipt.copy(replayed = true),
            success(f.retry().execute(f.actor, parent, 0, operation, device, 0, 0, client)),
        )
        // A normal native refresh changes authority version, not the command's identity.
        sql.update("update native_sessions set version=version+1 where id=?", parent)
        assertEquals(
            receipt.copy(replayed = true),
            success(f.retry().execute(f.actor, parent, 1, operation, device, 0, 0, client)),
        )
        val current = requireNotNull(success(f.status().execute(f.actor, parent, 1, client)))
        assertEquals(1L, current.provisioning?.version)
        assertEquals(receipt.id, current.provisioning?.jobId)
        assertEquals(0L, current.version)
        assertEquals(1, f.rows("collaboration_devices"))
        assertEquals(2, f.rows("background_jobs"))
        assertNull(f.credential(device))
        assertEquals("job_lease_lost", failed(f.advance(RecordingMatrix(f.clock)).execute(old)))
        sql.update(
            "delete from membership_permissions where company_id=? and account_id=?",
            f.actor.companyId,
            f.actor.accountId,
        )
        assertEquals("access_denied", failed(f.status().execute(f.actor, parent, 1, client)))
        assertEquals(
            "access_denied",
            failed(f.retry().execute(f.actor, parent, 1, operation, device, 0, 0, client)),
        )
    }

    @Test
    fun competingExplicitRetriesCreateExactlyOneJobAndProtectCompanyAndParentScope() {
        val f = Fixture()
        val parent = f.parent()
        val client = ClientVersion(ClientPlatform.ANDROID, 1)
        val device = success(f.register(parent)).id
        success(
            f.abort()
                .execute(
                    f.claim(device),
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested"),
                )
        )
        f.clock.at = f.clock.at.plusSeconds(61)
        val results =
            race(
                List(4) {
                    {
                        f.retry()
                            .execute(f.actor, parent, 0, UUID.randomUUID(), device, 0, 0, client)
                    }
                }
            )
        assertEquals(1, results.count { it is Result.Success })
        assertEquals(
            3,
            results.count {
                it is Result.Failed && it.failure.code == "collaboration_provisioning_changed"
            },
        )
        assertEquals(2, f.rows("background_jobs"))
        val another = f.parent()
        assertNull(success(f.status().execute(f.actor, another, 0, client)))
        assertEquals(
            "collaboration_access_denied",
            failed(f.retry().execute(f.actor, another, 0, UUID.randomUUID(), device, 0, 1, client)),
        )
        val secondCompany = f.company()
        val otherActor = f.actor.copy(companyId = secondCompany)
        assertNull(success(f.status().execute(otherActor, parent, 0, client)))
        assertEquals(
            "collaboration_access_denied",
            failed(
                f.retry().execute(otherActor, parent, 0, UUID.randomUUID(), device, 0, 1, client)
            ),
        )
        assertEquals(
            "invalid_collaboration_device",
            failed(f.status().execute(f.actor, parent, 0, ClientVersion(ClientPlatform.IOS, 1))),
        )
    }

    @Test
    fun unfinishedOrActiveProvisioningCannotBeResetAndStaleVersionDoesNotEnqueue() {
        val f = Fixture()
        val parent = f.parent()
        val client = ClientVersion(ClientPlatform.ANDROID, 1)
        val device = success(f.register(parent)).id
        f.clock.at = f.clock.at.plusSeconds(61)
        assertEquals(
            "collaboration_provisioning_changed",
            failed(f.retry().execute(f.actor, parent, 0, UUID.randomUUID(), device, 0, 0, client)),
        )
        val leased = f.claim(device)
        assertEquals(
            "collaboration_provisioning_changed",
            failed(f.retry().execute(f.actor, parent, 0, UUID.randomUUID(), device, 0, 0, client)),
        )
        success(f.advance(RecordingMatrix(f.clock)).execute(leased))
        val active = requireNotNull(success(f.status().execute(f.actor, parent, 0, client)))
        assertEquals(CollaborationDeviceState.ACTIVE, active.state)
        assertNull(active.provisioning?.retryNotBefore)
        assertEquals(
            "collaboration_provisioning_changed",
            failed(f.retry().execute(f.actor, parent, 0, UUID.randomUUID(), device, 1, 0, client)),
        )
        assertEquals(1, f.rows("background_jobs"))
    }

    @Test
    fun retryPostWriteExpiryRollsBackNewJobPointerReceiptAuditAndOutbox() {
        val f = Fixture()
        val parent = f.parent()
        val device = success(f.register(parent)).id
        val old = f.claim(device)
        success(
            f.abort()
                .execute(
                    old,
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable"),
                )
        )
        f.clock.at = f.clock.at.plusSeconds(61)
        val before =
            listOf("background_jobs", "operation_receipts", "audit_entries", "outbox_events")
                .associateWith(f::rows)
        val journal =
            object : ChangeJournalRepository by f.journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
                    val result = f.journal.record(actor, change)
                    f.clock.at = f.clock.at.plusSeconds(3600)
                    return result
                }
            }
        assertTrue(
            f.retry(journal)
                .execute(
                    f.actor,
                    parent,
                    0,
                    UUID.randomUUID(),
                    device,
                    0,
                    0,
                    ClientVersion(ClientPlatform.ANDROID, 1),
                ) is Result.Failed
        )
        assertEquals(before, before.keys.associateWith(f::rows))
        val pointer =
            success(
                f.transactions.run(f.actor) {
                    f.provisioning.find(
                        requireNotNull(f.actor.companyId),
                        f.actor.accountId,
                        device,
                    )
                }
            )
        assertEquals(old.job.request.id, pointer?.jobId)
        assertEquals(0L, pointer?.version)
    }

    @Test
    fun fullCompanyQueueLeavesFailedPointerAndOperationUnchanged() {
        val f = Fixture()
        val parent = f.parent()
        val device = success(f.register(parent)).id
        val old = f.claim(device)
        success(
            f.abort()
                .execute(
                    old,
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable"),
                )
        )
        f.clock.at = f.clock.at.plusSeconds(61)
        success(
            f.transactions.run(f.actor) {
                repeat(100) {
                    val request =
                        JobRequest(
                            UUID.randomUUID(),
                            requireNotNull(f.actor.companyId),
                            f.account,
                            JobKind.REPORT_EXPORT,
                            UUID.randomUUID(),
                            emptyMap(),
                            f.actor.authenticatedAt,
                            0,
                            f.actor.correlationId,
                            f.clock.at,
                            1,
                        )
                    val created = f.jobs.create(request)
                    if (created is Result.Failed) return@run created
                }
                Result.Success(Unit)
            }
        )
        val tables =
            listOf("background_jobs", "operation_receipts", "audit_entries", "outbox_events")
        val before = tables.associateWith(f::rows)
        assertEquals(
            "job_queue_full",
            failed(
                f.retry()
                    .execute(
                        f.actor,
                        parent,
                        0,
                        UUID.randomUUID(),
                        device,
                        0,
                        0,
                        ClientVersion(ClientPlatform.ANDROID, 1),
                    )
            ),
        )
        assertEquals(before, tables.associateWith(f::rows))
        val pointer =
            success(
                f.transactions.run(f.actor) {
                    f.provisioning.find(requireNotNull(f.actor.companyId), f.account, device)
                }
            )
        assertEquals(old.job.request.id, pointer?.jobId)
        assertEquals(0L, pointer?.version)
    }

    private class RecordingMatrix(private val clock: Clock) : CollaborationMatrixRepository {
        val logins = AtomicInteger()
        val logouts = AtomicInteger()
        var onLogin: (CollaborationMatrixLogin) -> Result<CollaborationMatrixCredential> = ::issue

        fun issue(request: CollaborationMatrixLogin): Result<CollaborationMatrixCredential> =
            Result.Success(
                CollaborationMatrixCredential(
                    "@${collaborationMatrixLocalpart(request.companyId, request.accountId)}:proof.invalid",
                    collaborationMatrixDeviceId(request.deviceId),
                    "fictional-provider-${UUID.randomUUID()}",
                    clock.instant().plusSeconds(300),
                )
            )

        override fun login(
            request: CollaborationMatrixLogin
        ): Result<CollaborationMatrixCredential> {
            logins.incrementAndGet()
            return onLogin(request)
        }

        override fun logoutDevice(accessToken: String): Result<Unit> {
            logouts.incrementAndGet()
            return Result.Success(Unit)
        }
    }

    @Test
    fun gatewayBootstrapReplaysTheSameSecretAfterNativeRefreshAndNeverWritesSecretsToReceipts() {
        val g = GatewayFixture()
        val f = g.f
        val tokens = success(g.bootstrap())
        sql.update("update native_sessions set version=version+1 where id=?", g.parent)
        val replay = success(g.bootstrap(parentVersion = 1))
        assertEquals(tokens.accessToken, replay.accessToken)
        assertEquals(tokens.refreshToken, replay.refreshToken)
        assertEquals(0L, replay.version)
        assertEquals(1, f.rows("collaboration_gateway_sessions"))
        assertEquals(1, f.rows("collaboration_devices"))
        val receipts =
            sql.queryForList(
                    "select row_to_json(r)::text from operation_receipts r where company_id=?",
                    String::class.java,
                    f.actor.companyId,
                )
                .joinToString()
        assertFalse(receipts.contains(tokens.accessToken))
        assertFalse(receipts.contains(tokens.refreshToken))
        assertEquals(tokens.sessionId, f.gatewayHead(g.device).id)
        assertEquals(1L, f.gatewayHead(g.device).clientBuild.toLong())
    }

    @Test
    fun concurrentGatewayBootstrapsPublishOneHeadAndExpiredOperationsCannotReplaceIt() {
        val g = GatewayFixture()
        val responses = race(List(4) { { g.bootstrap() } }).map(::success)
        assertEquals(1, responses.map { it.accessToken }.distinct().size)
        assertEquals(1, responses.map { it.refreshToken }.distinct().size)
        assertEquals(2, g.f.rows("operation_receipts")) // registration and bootstrap
        g.f.clock.at = g.f.clock.at.plusSeconds(31)
        assertEquals("collaboration_gateway_authentication_required", failed(g.bootstrap()))
        assertEquals(0L, g.f.gatewayHead(g.device).version)
        val next = success(g.bootstrap(operation = UUID.randomUUID(), expected = 0, build = 2))
        assertEquals(1L, next.version)
        assertEquals(2, g.f.gatewayHead(g.device).clientBuild)
        assertEquals("collaboration_gateway_changed", failed(g.bootstrap()))
        assertEquals(
            "collaboration_gateway_authentication_required",
            failed(g.refresh(responses.first().refreshToken)),
        )
        assertEquals(1, g.f.rows("collaboration_devices"))
    }

    @Test
    fun competingDifferentBootstrapOperationsRequireTheObservedGatewayVersion() {
        val g = GatewayFixture()
        val responses = race(List(4) { { g.bootstrap(operation = UUID.randomUUID()) } })
        assertEquals(1, responses.count { it is Result.Success })
        assertTrue(
            responses.filterIsInstance<Result.Failed>().all {
                it.failure.code == "collaboration_gateway_changed"
            }
        )
        assertEquals(2, g.f.rows("operation_receipts"))
        assertEquals(1, g.f.rows("collaboration_gateway_sessions"))
    }

    @Test
    fun gatewayRefreshRaceAndLostResponseReturnOnePairWithoutReplacingTheNativeOrMatrixDevice() {
        val g = GatewayFixture()
        val first = success(g.bootstrap())
        assertEquals(
            "collaboration_gateway_rotation_too_soon",
            failed(g.refresh(first.refreshToken)),
        )
        g.f.clock.at = g.f.clock.at.plusSeconds(11)
        val results = race(List(4) { { g.refresh(first.refreshToken) } }).map(::success)
        assertEquals(1, results.map { it.accessToken }.distinct().size)
        assertEquals(1, results.map { it.refreshToken }.distinct().size)
        assertTrue(results.all { it.version == 1L && it.sessionId == first.sessionId })
        assertEquals(3, g.f.rows("audit_entries")) // registration, bootstrap, refresh
        assertEquals(2, g.f.rows("operation_receipts"))
        assertEquals(
            0L,
            sql.queryForObject(
                "select version from native_sessions where id=?",
                Long::class.java,
                g.parent,
            ),
        )
        assertEquals("ACTIVE", g.f.state(g.device))
        assertEquals(1, g.f.rows("collaboration_devices"))
    }

    @Test
    fun gatewayAccessExpiryAllowsRefreshButAnExpiredPreviousRefreshCannotRevokeTheWinner() {
        val g = GatewayFixture()
        val first = success(g.bootstrap())
        g.f.clock.at = g.f.clock.at.plusSeconds(301)
        val next = success(g.refresh(first.refreshToken))
        g.f.clock.at = g.f.clock.at.plusSeconds(30)
        assertEquals(
            "collaboration_gateway_authentication_required",
            failed(g.refresh(first.refreshToken)),
        )
        assertEquals(1L, g.f.gatewayHead(g.device).version)
        val latest = success(g.refresh(next.refreshToken))
        assertEquals(2L, latest.version)
        assertEquals(
            "collaboration_gateway_authentication_required",
            failed(g.refresh(first.refreshToken)),
        )
        assertEquals("ACTIVE", g.f.state(g.device))
        assertNull(g.f.gatewayHead(g.device).revokedAt)
    }

    @Test
    fun gatewayCannotRefreshAnExpiredHrisAccessSessionButResumesAfterItsOrdinaryNativeRefresh() {
        val g = GatewayFixture()
        val first = success(g.bootstrap())
        g.f.clock.at = g.f.clock.at.plusSeconds(901)
        assertEquals("native_session_invalid", failed(g.refresh(first.refreshToken)))
        assertEquals(0L, g.f.gatewayHead(g.device).version)
        sql.update(
            "update native_sessions set version=version+1,access_expires_at=? where id=?",
            g.f.clock.at.plusSeconds(900).atOffset(ZoneOffset.UTC),
            g.parent,
        )
        val next = success(g.refresh(first.refreshToken))
        assertEquals(1L, next.version)
        assertEquals(first.sessionId, next.sessionId)
        assertEquals("ACTIVE", g.f.state(g.device))
    }

    @Test
    fun gatewayRechecksPermissionMfaBuildCompanyAndDeviceForBothBootstrapReplayAndRefresh() {
        for (mode in 0..5) {
            val g = GatewayFixture()
            val first = success(g.bootstrap())
            g.f.clock.at = g.f.clock.at.plusSeconds(11)
            val expected =
                when (mode) {
                    0 -> {
                        sql.update(
                            "delete from membership_permissions where company_id=? and account_id=?",
                            g.f.actor.companyId,
                            g.f.account,
                        )
                        "access_denied"
                    }
                    1 -> {
                        sql.update(
                            "update accounts set mfa_secret_encrypted='fictional-configured-secret' where id=?",
                            g.f.account,
                        )
                        "mfa_required"
                    }
                    2 -> {
                        g.f.policy(ClientPolicy(minimumBuilds = MinimumClientBuilds(android = 2)))
                        "client_update_required"
                    }
                    3 -> {
                        g.f.policy(
                            ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION))
                        )
                        "company_module_disabled"
                    }
                    4 -> {
                        sql.update(
                            "update collaboration_devices set state='REVOKED',version=version+1,revoked_at=?,updated_at=? where id=?",
                            g.f.clock.at.atOffset(ZoneOffset.UTC),
                            g.f.clock.at.atOffset(ZoneOffset.UTC),
                            g.device,
                        )
                        "collaboration_access_denied"
                    }
                    else -> {
                        sql.update(
                            "update accounts set security_version=security_version+1 where id=?",
                            g.f.account,
                        )
                        "session_revoked"
                    }
                }
            assertEquals(expected, failed(g.bootstrap()), "bootstrap mode $mode")
            assertEquals(expected, failed(g.refresh(first.refreshToken)), "refresh mode $mode")
            assertEquals(0L, g.f.gatewayHead(g.device).version)
        }
    }

    @Test
    fun gatewayRevocationBetweenUnscopedTokenLookupAndGuardedRefreshIsRejected() {
        val g = GatewayFixture()
        val first = success(g.bootstrap())
        g.f.clock.at = g.f.clock.at.plusSeconds(11)
        val store =
            object : CollaborationGatewaySessionRepository by g.f.gateway {
                override fun findRefresh(token: String): Result<CollaborationGatewayRefresh?> =
                    g.f.gateway.findRefresh(token).also {
                        sql.update(
                            "update native_sessions set revoked_at=? where id=?",
                            g.f.clock.at.atOffset(ZoneOffset.UTC),
                            g.parent,
                        )
                    }
            }
        assertEquals(
            "native_session_invalid",
            failed(
                g.f.gatewayRefresh(gateway = store).execute(first.refreshToken, UUID.randomUUID())
            ),
        )
        assertEquals(0L, g.f.gatewayHead(g.device).version)
        assertEquals("ACTIVE", g.f.state(g.device))
    }

    @Test
    fun gatewayIssuanceRollsBackTokensReceiptsAndJournalsWhenAuthorityExpiresAfterWrites() {
        for (refresh in listOf(false, true)) {
            val g = GatewayFixture()
            val first = if (refresh) success(g.bootstrap()) else null
            val tables =
                listOf(
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                    "collaboration_gateway_sessions",
                )
            val before = tables.associateWith { g.f.rows(it) }
            g.f.clock.at = g.f.clock.at.plusSeconds(11)
            val journal =
                object : ChangeJournalRepository {
                    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                        g.f.journal.record(actor, change).also {
                            g.f.clock.at = g.f.clock.at.plusSeconds(3600)
                        }
                }
            val result =
                if (refresh)
                    g.f
                        .gatewayRefresh(journal = journal)
                        .execute(requireNotNull(first).refreshToken, UUID.randomUUID())
                else
                    g.f
                        .bootstrap(journal = journal)
                        .execute(
                            g.f.actor,
                            g.parent,
                            0,
                            g.operation,
                            g.device,
                            1,
                            null,
                            ClientVersion(ClientPlatform.ANDROID, 1),
                        )
            assertEquals("native_session_invalid", failed(result))
            assertEquals(before, tables.associateWith { g.f.rows(it) })
            if (refresh) assertEquals(0L, g.f.gatewayHead(g.device).version)
        }
    }

    @Test
    fun scheduledCompanyPolicyAtGatewayPublicationRollsBackAndBootstrapRejectsForeignDevices() {
        val g = GatewayFixture()
        val second = GatewayFixture()
        assertEquals(
            "collaboration_access_denied",
            failed(
                g.f
                    .bootstrap()
                    .execute(
                        g.f.actor,
                        g.parent,
                        0,
                        UUID.randomUUID(),
                        second.device,
                        1,
                        null,
                        ClientVersion(ClientPlatform.ANDROID, 1),
                    )
            ),
        )
        assertEquals(
            "invalid_client_version",
            failed(
                g.f
                    .bootstrap()
                    .execute(g.f.actor, g.parent, 0, UUID.randomUUID(), g.device, 1, null, null)
            ),
        )
        assertEquals(
            "collaboration_unavailable",
            failed(
                g.f
                    .bootstrap(provider = CollaborationMatrixPolicy(false, ""))
                    .execute(
                        g.f.actor,
                        g.parent,
                        0,
                        UUID.randomUUID(),
                        g.device,
                        1,
                        null,
                        ClientVersion(ClientPlatform.ANDROID, 1),
                    )
            ),
        )
        val boundary = g.f.clock.at.plusSeconds(10)
        g.f.policy(ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)), boundary)
        val journal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    g.f.journal.record(actor, change).also { g.f.clock.at = boundary }
            }
        assertEquals(
            "company_module_disabled",
            failed(
                g.f
                    .bootstrap(journal = journal)
                    .execute(
                        g.f.actor,
                        g.parent,
                        0,
                        g.operation,
                        g.device,
                        1,
                        null,
                        ClientVersion(ClientPlatform.ANDROID, 1),
                    )
            ),
        )
        assertEquals(0, g.f.rows("collaboration_gateway_sessions"))
        assertEquals(1, g.f.rows("operation_receipts"))
    }

    private class GatewayFixture {
        val f = Fixture()
        val parent = f.parent()
        val device = (f.register(parent) as Result.Success).value.id
        val operation = UUID.randomUUID()

        init {
            f.activate(device)
        }

        fun bootstrap(
            parentVersion: Long = 0,
            operation: UUID = this.operation,
            expected: Long? = null,
            build: Int = 1,
        ) =
            f.bootstrap()
                .execute(
                    f.actor,
                    parent,
                    parentVersion,
                    operation,
                    device,
                    1,
                    expected,
                    ClientVersion(ClientPlatform.ANDROID, build),
                )

        fun refresh(token: String) = f.gatewayRefresh().execute(token, UUID.randomUUID())
    }

    private class Fixture {
        val clock = MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val account = UUID.randomUUID()
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
        private val dsl = DSL.using(TransactionAwareDataSourceProxy(runtime), SQLDialect.POSTGRES)
        private val json = JsonMapper.builder().build()
        private val receipts = PostgresOperationReceiptDataSource(dsl)
        val registry =
            StoredCollaborationRegistryRepository(PostgresCollaborationRegistryDataSource(dsl))
        val policies =
            StoredCompanyClientPolicyRepository(PostgresCompanyClientPolicyDataSource(dsl))
        val identities =
            StoredIdentityRepository(
                PostgresIdentityDataSource(dsl),
                object : PasswordDataSource {
                    override fun hash(password: String): String =
                        error("Admission does not hash passwords")

                    override fun matches(password: String, hash: String): Boolean =
                        error("Admission does not verify passwords")
                },
                "unused",
            )
        private val sessions =
            StoredNativeSessionRepository(
                PostgresNativeSessionDataSource(dsl),
                object : IdentityTokenDataSource {
                    override fun generate(): String =
                        error("Admission does not issue native tokens")

                    override fun hash(token: String): String =
                        error("Admission receives authenticated parent identity")

                    override fun encrypt(purpose: String, value: String): String =
                        error("No native token material")

                    override fun decrypt(purpose: String, value: String): String =
                        error("No native token material")
                },
            )
        private val companies =
            StoredCompanyRepository(PostgresCompanyDataSource(dsl), receipts, json)
        private val members = StoredMembershipRepository(PostgresMembershipDataSource(dsl))
        val provisioning =
            StoredCollaborationProvisioningRepository(
                PostgresCollaborationProvisioningDataSource(dsl)
            )
        val jobs = PostgresJobRepository(PostgresJobDataSource(dsl), json)
        val credentials =
            EncryptedCollaborationProviderSessionRepository(
                PostgresCollaborationProviderSessionDataSource(dsl),
                JceIdentityTokenDataSource(
                    dev.fajar.hris.identity.data.crypto.parseIdentityKeyring(
                        "fixture",
                        "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 17 }),
                    )
                ),
            )
        val gateway =
            EncryptedCollaborationGatewaySessionRepository(
                PostgresCollaborationGatewaySessionDataSource(dsl),
                JceIdentityTokenDataSource(
                    dev.fajar.hris.identity.data.crypto.parseIdentityKeyring(
                        "fixture",
                        "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 17 }),
                    )
                ),
            )
        private val operations = PostgresOperationRepository(receipts, json)
        val journal = PostgresChangeJournalRepository(PostgresChangeJournalDataSource(dsl, json))
        val actor: Actor

        init {
            sql.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional collaboration employee')",
                account,
                "$account@example.test",
            )
            actor =
                Actor(
                    account,
                    company(),
                    setOf("collaboration.chat"),
                    clock.at,
                    UUID.randomUUID(),
                    credentialVersion = 0,
                )
        }

        fun company(): UUID {
            val id = UUID.randomUUID()
            sql.update(
                "insert into companies(id,code,name,timezone) values(?,?, 'Collaboration fixture', 'UTC')",
                id,
                id.toString().take(12),
            )
            sql.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                id,
                account,
            )
            sql.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                id,
                account,
            )
            return id
        }

        fun parent(): UUID {
            val id = UUID.randomUUID()
            val hash = id.toString().replace("-", "").repeat(2)
            sql.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,access_hash,access_expires_at,credential_version,created_at,rotated_at) values(?,?,?,now()-interval '1 minute',now()+interval '1 day',?,now()+interval '15 minutes',0,now()-interval '1 minute',now()-interval '1 minute')",
                id,
                account,
                hash,
                hash,
            )
            return id
        }

        fun command(
            journal: ChangeJournalRepository = this.journal,
            identities: IdentityRepository = this.identities,
            service: CollaborationServicePolicy = CollaborationServicePolicy(enabled = true),
        ) =
            RegisterNativeCollaborationDevice(
                registry,
                provisioning,
                jobs,
                sessions,
                policies,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                IdentitySecurityPolicy(),
                clock,
                service,
            )

        fun status() =
            GetNativeCollaborationDeviceStatus(
                registry,
                provisioning,
                jobs,
                sessions,
                policies,
                companies,
                members,
                identities,
                transactions,
                IdentitySecurityPolicy(),
                clock,
                CollaborationServicePolicy(true),
            )

        fun bootstrap(
            journal: ChangeJournalRepository = this.journal,
            provider: CollaborationMatrixPolicy = CollaborationMatrixPolicy(true, "fixture.test"),
        ) =
            BootstrapNativeCollaborationGateway(
                gateway,
                registry,
                sessions,
                policies,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                IdentitySecurityPolicy(),
                CollaborationServicePolicy(true),
                provider,
                clock,
            )

        fun gatewayRefresh(
            gateway: CollaborationGatewaySessionRepository = this.gateway,
            journal: ChangeJournalRepository = this.journal,
        ) =
            RefreshNativeCollaborationGateway(
                gateway,
                registry,
                sessions,
                policies,
                companies,
                members,
                identities,
                journal,
                transactions,
                IdentitySecurityPolicy(),
                CollaborationServicePolicy(true),
                CollaborationMatrixPolicy(true, "fixture.test"),
                clock,
            )

        fun gatewayHead(device: UUID) =
            requireNotNull(
                success(
                    transactions.run(actor) {
                        gateway.find(requireNotNull(actor.companyId), account, device)
                    }
                )
            )

        fun retry(journal: ChangeJournalRepository = this.journal) =
            RetryNativeCollaborationProvisioning(
                registry,
                provisioning,
                jobs,
                sessions,
                policies,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                IdentitySecurityPolicy(),
                clock,
                CollaborationServicePolicy(true),
            )

        fun advance(
            matrix: CollaborationMatrixRepository,
            journal: ChangeJournalRepository = this.journal,
            policies: CompanyClientPolicyRepository = this.policies,
        ) =
            AdvanceNativeCollaborationProvisioning(
                registry,
                provisioning,
                credentials,
                matrix,
                jobs,
                sessions,
                policies,
                companies,
                members,
                identities,
                journal,
                transactions,
                IdentitySecurityPolicy(),
                CollaborationServicePolicy(enabled = true),
                CollaborationMatrixPolicy(true, "proof.invalid"),
                clock,
            )

        fun abort() = AbortCollaborationProvisioning(jobs, journal, transactions)

        fun claim(device: UUID): JobLease {
            val pointer =
                requireNotNull(
                    success(
                        transactions.run(actor) {
                            provisioning.find(requireNotNull(actor.companyId), account, device)
                        }
                    )
                )
            val owner = UUID.randomUUID()
            val token = UUID.randomUUID()
            val deadline = Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.MICROS)
            assertEquals(
                1,
                sql.update(
                    "update background_jobs set status='RUNNING',attempts=attempts+1,lease_owner=?,lease_token=?,lease_until=?,version=version+1 where id=? and status in ('QUEUED','RUNNING')",
                    owner,
                    token,
                    deadline.atOffset(ZoneOffset.UTC),
                    pointer.jobId,
                ),
            )
            val job =
                requireNotNull(
                    success(
                        transactions.run(actor) {
                            jobs.find(requireNotNull(actor.companyId), pointer.jobId)
                        }
                    )
                )
            return JobLease(job, owner, token, deadline)
        }

        fun job(lease: JobLease) =
            requireNotNull(
                success(
                    transactions.run(actor) {
                        jobs.find(requireNotNull(actor.companyId), lease.job.request.id)
                    }
                )
            )

        fun credential(device: UUID) =
            success(
                transactions.run(actor) {
                    credentials.find(requireNotNull(actor.companyId), account, device)
                }
            )

        fun state(device: UUID) =
            sql.queryForObject(
                "select state from collaboration_devices where id=?",
                String::class.java,
                device,
            )

        private fun <T> success(value: Result<T>): T {
            assertInstanceOf(Result.Success::class.java, value)
            return (value as Result.Success).value
        }

        fun register(
            parent: UUID,
            operation: UUID = UUID.randomUUID(),
            version: Long = 0,
            label: String = "Fictional emulator",
            actor: Actor = this.actor,
            client: ClientVersion = ClientVersion(ClientPlatform.ANDROID, 1),
            platform: CollaborationPlatform = CollaborationPlatform.ANDROID,
            command: RegisterNativeCollaborationDevice = command(),
        ) = command.execute(actor, parent, version, operation, platform, label, client)

        fun acquire(
            parent: UUID,
            policies: CompanyClientPolicyRepository = this.policies,
            client: ClientVersion = ClientVersion(ClientPlatform.ANDROID, 1),
        ) =
            AcquireNativeCollaborationAdmission(
                    registry,
                    sessions,
                    policies,
                    companies,
                    members,
                    identities,
                    transactions,
                    IdentitySecurityPolicy(),
                    clock,
                    CollaborationServicePolicy(enabled = true),
                )
                .execute(actor, parent, 0, client)

        fun activate(device: UUID) {
            sql.update(
                "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=now() where id=?",
                device,
            )
        }

        fun policy(policy: ClientPolicy, at: Instant = clock.at) {
            val company = requireNotNull(actor.companyId)
            assertInstanceOf(
                Result.Success::class.java,
                transactions.run(actor) {
                    policies.save(
                        company,
                        CompanyClientPolicyRevision(
                            0,
                            at,
                            policy,
                            clock.at,
                            account,
                            "Fictional policy fixture",
                        ),
                        null,
                    )
                },
            )
        }

        fun rows(table: String): Int =
            requireNotNull(
                sql.queryForObject(
                    "select count(*) from $table where company_id=?",
                    Int::class.java,
                    actor.companyId,
                )
            )
    }

    private class MutableClock(@Volatile var at: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = at
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    private fun failed(result: Result<*>): String {
        assertInstanceOf(Result.Failed::class.java, result)
        return (result as Result.Failed).failure.code
    }

    private fun <T> race(actions: List<() -> T>): List<T> {
        val start = CyclicBarrier(actions.size)
        val executor = Executors.newFixedThreadPool(actions.size)
        try {
            val futures =
                actions.map { action ->
                    executor.submit<T> {
                        start.await(5, TimeUnit.SECONDS)
                        action()
                    }
                }
            return futures.map { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
