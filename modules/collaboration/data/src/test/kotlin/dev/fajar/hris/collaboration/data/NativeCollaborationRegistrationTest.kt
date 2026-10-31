package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.data.datasources.PostgresCompanyClientPolicyDataSource
import dev.fajar.hris.administration.data.repositories.StoredCompanyClientPolicyRepository
import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationDeviceKeyDataSource
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationDirectDataSource
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationDirectRoomDataSource
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationGatewaySessionDataSource
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationProviderSessionDataSource
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationProvisioningDataSource
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationRecipientDataSource
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationRegistryDataSource
import dev.fajar.hris.collaboration.data.matrix.HttpMatrixClientDataSource
import dev.fajar.hris.collaboration.data.repositories.DisabledCollaborationMatrixKeyRepository
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationGatewaySessionRepository
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationProviderSessionRepository
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixKeyRepository
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationDeviceKeyRepository
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationDirectRepository
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationDirectRoomRepository
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationProvisioningRepository
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationRecipientRepository
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationRegistryRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationDirectRoomPower
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixDeviceId
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.collaboration.domain.policies.validateCollaborationDeviceKeys
import dev.fajar.hris.collaboration.domain.repositories.CollaborationDeviceKeyRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationDirectRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationDirectRoomRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationGatewaySessionRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixKeyRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomRepository
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.core.database.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
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
        val queries = AtomicInteger()
        val issued = ConcurrentHashMap<String, CollaborationMatrixIdentity>()
        var onQuery: (String) -> Result<CollaborationMatrixIdentity> = {
            Result.Success(requireNotNull(issued[it]))
        }
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
                .also {
                    issued[it.value.accessToken] =
                        CollaborationMatrixIdentity(it.value.userId, it.value.deviceId, false)
                }

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

        override fun whoAmI(
            accessToken: String,
            deadline: Instant,
        ): Result<CollaborationMatrixIdentity> {
            assertTrue(clock.instant() < deadline)
            queries.incrementAndGet()
            return onQuery(accessToken)
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

    @Test
    fun gatewayKeyReadbackCannotConfirmMissingChangedLateOrUnavailableProviderIdentity() {
        for (mode in listOf("missing", "changed", "expired", "unavailable")) {
            val k = KeyUploadFixture()
            val replacement = MatrixKeyFixture(k.keys.userId, k.keys.deviceId).device
            k.provider.onQuery = {
                if (k.provider.queries.get() != 2) Result.Success(k.provider.stored)
                else
                    when (mode) {
                        "missing" -> Result.Success(null)
                        "changed" -> Result.Success(replacement)
                        "expired" -> {
                            k.g.f.clock.at = k.g.f.clock.instant().plusSeconds(60)
                            Result.Success(k.provider.stored)
                        }
                        else ->
                            Result.Failed(
                                Failure(FailureKind.UNAVAILABLE, "fixture_readback_unavailable")
                            )
                    }
            }
            val result = k.upload()
            assertEquals(
                when (mode) {
                    "missing" -> "collaboration_provider_invalid_response"
                    "changed" -> "collaboration_device_key_recovery_required"
                    "expired" -> "collaboration_admission_changed"
                    else -> "fixture_readback_unavailable"
                },
                failed(result),
                mode,
            )
            assertEquals(0L, k.publication()?.version, mode)
            assertEquals(1, k.provider.uploads.get())
            assertEquals(0, k.matrix.logouts.get())
            k.provider.onQuery = null
            assertInstanceOf(Result.Success::class.java, k.upload(), mode)
            assertEquals(1L, k.publication()?.version)
        }
    }

    @Test
    fun gatewayRepeatedSameIdentityUploadsShareOnePublicationAndCredentialGeneration() {
        val k = KeyUploadFixture()
        val before = k.g.f.rows("audit_entries")
        val ready = CyclicBarrier(2)
        k.matrix.onLogin = { request ->
            ready.await(10, TimeUnit.SECONDS)
            k.matrix.issue(request)
        }
        val outcomes = race(listOf({ k.upload() }, { k.upload() }))
        assertTrue(outcomes.all { it is Result.Success }, outcomes.toString())
        assertEquals(1L, k.publication()?.version)
        assertEquals(k.keys.device, k.provider.stored)
        assertEquals(before + 3, k.g.f.rows("audit_entries"))
        assertEquals(2, k.matrix.logins.get())
        assertEquals(0L, k.g.f.credential(k.g.device)?.generation)
        assertEquals(0, k.matrix.logouts.get())
    }

    @Test
    fun gatewayKeyCountsAndRepeatedUploadPublishExactlyOneRetainedIdentity() {
        val k = KeyUploadFixture()
        assertInstanceOf(Result.Success::class.java, k.upload(CollaborationMatrixKeyUpload()))
        assertNull(k.publication())
        assertEquals(0, k.provider.queries.get())
        val before = k.g.f.rows("audit_entries")
        val first = success(k.upload())
        assertEquals(1, first.oneTimeKeyCounts["signed_curve25519"])
        val published = requireNotNull(k.publication())
        assertEquals(1L, published.version)
        assertNotNull(published.publishedAt)
        assertEquals(first, success(k.upload()))
        assertEquals(published, k.publication())
        assertEquals(before + 2, k.g.f.rows("audit_entries"))
        assertEquals(1, k.matrix.logins.get())
        assertEquals(0, k.matrix.logouts.get())
        assertInstanceOf(
            Result.Success::class.java,
            k.upload(
                CollaborationMatrixKeyUpload(
                    oneTimeKeys = mapOf("signed_curve25519:2" to k.keys.signed())
                )
            ),
        )
        assertEquals(published, k.publication())
    }

    @Test
    fun gatewayKeysRejectWrongOwnerDeviceSignatureAndUnenrolledOneTimeKeysBeforeProviderWrites() {
        val k = KeyUploadFixture()
        for (device in
            listOf(
                MatrixKeyFixture("@foreign:proof.invalid", k.keys.deviceId).device,
                MatrixKeyFixture(k.keys.userId, "HRIS_" + "f".repeat(32)).device,
                k.keys.device.copy(
                    keys =
                        k.keys.device.keys +
                            ("curve25519:${k.keys.deviceId}" to k.keys.signed().key)
                ),
            )) {
            assertEquals(
                "collaboration_device_keys_invalid",
                failed(k.upload(CollaborationMatrixKeyUpload(device))),
            )
        }
        assertEquals(
            "collaboration_device_keys_required",
            failed(k.upload(CollaborationMatrixKeyUpload(oneTimeKeys = k.request.oneTimeKeys))),
        )
        assertNull(k.publication())
        assertEquals(0, k.matrix.logins.get())
        assertEquals(0, k.provider.uploads.get())
        val signed = k.keys.signed()
        assertEquals(
            "collaboration_device_keys_invalid",
            failed(
                k.upload(
                    k.request.copy(
                        oneTimeKeys =
                            mapOf("signed_curve25519:bad" to signed.copy(key = k.keys.signed().key))
                    )
                )
            ),
        )
        assertEquals(0, k.provider.uploads.get())
        assertEquals(0L, k.publication()?.version)
    }

    @Test
    fun gatewayKeyResponseLossRetainsReservationAndRefusesReplacementBeforeReplay() {
        val k = KeyUploadFixture()
        k.provider.onUpload = { request ->
            k.provider.accept(request)
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_lost_upload"))
        }
        assertEquals("fixture_lost_upload", failed(k.upload()))
        val pending = requireNotNull(k.publication())
        assertEquals(0L, pending.version)
        assertEquals(k.keys.device, k.provider.stored)
        val replacement = MatrixKeyFixture(k.keys.userId, k.keys.deviceId)
        assertEquals(
            "collaboration_device_key_recovery_required",
            failed(k.upload(CollaborationMatrixKeyUpload(replacement.device))),
        )
        assertEquals(1, k.provider.uploads.get())
        k.provider.onUpload = null
        assertInstanceOf(Result.Success::class.java, k.upload())
        assertEquals(
            pending.copy(version = 1, publishedAt = k.g.f.clock.instant()),
            k.publication(),
        )
        assertEquals(k.keys.device, k.provider.stored)
        assertEquals(2, k.provider.uploads.get())
        assertEquals(0, k.matrix.logouts.get())
    }

    @Test
    fun gatewayKeysNeverOverwriteConflictingPreexistingProviderIdentity() {
        val k = KeyUploadFixture()
        val prior = MatrixKeyFixture(k.keys.userId, k.keys.deviceId).device
        k.provider.stored = prior
        assertEquals("collaboration_device_key_recovery_required", failed(k.upload()))
        assertEquals(prior, k.provider.stored)
        assertEquals(0, k.provider.uploads.get())
        assertEquals(0L, k.publication()?.version)
    }

    @Test
    fun gatewayKeysWithholdProviderFailuresAndSuccessAfterPermissionOrParentChanges() {
        for (stage in listOf("query", "upload")) {
            val k = KeyUploadFixture()
            fun deny(): Result.Failed {
                sql.update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                    k.g.f.actor.companyId,
                    k.g.f.account,
                )
                return Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_failed"))
            }
            if (stage == "query") k.provider.onQuery = { deny() }
            else
                k.provider.onUpload = { request ->
                    k.provider.accept(request)
                    deny()
                }
            assertEquals("access_denied", failed(k.upload()))
            assertEquals(0L, k.publication()?.version)
            assertEquals(if (stage == "query") 0 else 1, k.provider.uploads.get())
            assertEquals(0, k.matrix.logouts.get())
        }
        val k = KeyUploadFixture()
        k.provider.onUpload = { request ->
            val response = k.provider.accept(request)
            sql.update(
                "update native_sessions set version=version+1,rotated_at=now() where id=?",
                k.g.parent,
            )
            response
        }
        val changed = k.upload()
        assertEquals("collaboration_admission_changed", failed(changed))
        assertEquals(FailureKind.UNAVAILABLE, (changed as Result.Failed).failure.kind)
        assertEquals(0L, k.publication()?.version)
        k.provider.onUpload = null
        assertInstanceOf(Result.Success::class.java, k.upload())
        assertEquals(1L, k.publication()?.version)
        assertEquals(0, k.matrix.logouts.get())
    }

    @Test
    fun gatewayKeyLateReservationAndInterruptedPublicationRollBackTheirOwnJournal() {
        val late = KeyUploadFixture()
        val before = late.g.f.rows("audit_entries")
        val deadline = late.g.f.clock.instant().plusSeconds(5)
        late.g.f.policy(
            ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)),
            deadline,
        )
        val lateJournal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    late.g.f.journal.record(actor, change).also { late.g.f.clock.at = deadline }
            }
        val lateCase =
            late.g.f.matrixIdentity(
                late.matrix,
                journal = lateJournal,
                providerKeys = late.provider,
            )
        assertInstanceOf(
            Result.Failed::class.java,
            lateCase.uploadKeys(late.tokens.accessToken, UUID.randomUUID(), late.request),
        )
        assertNull(late.publication())
        assertEquals(before, late.g.f.rows("audit_entries"))
        assertEquals(0, late.matrix.logins.get())
        val k = KeyUploadFixture()
        val journal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    k.g.f.journal.record(actor, change).also {
                        if (change.action == "collaboration.device_keys_published")
                            throw InterruptedException("fixture cancellation")
                    }
            }
        val command = k.g.f.matrixIdentity(k.matrix, journal = journal, providerKeys = k.provider)
        try {
            assertThrows(InterruptedException::class.java) {
                command.uploadKeys(k.tokens.accessToken, UUID.randomUUID(), k.request)
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(0L, k.publication()?.version)
        assertEquals(k.keys.device, k.provider.stored)
        val pendingAudit = k.g.f.rows("audit_entries")
        assertInstanceOf(Result.Success::class.java, k.upload())
        assertEquals(1L, k.publication()?.version)
        assertEquals(pendingAudit + 1, k.g.f.rows("audit_entries"))
    }

    @Test
    fun gatewayCompetingFirstKeyUploadsCannotReplaceWinningFingerprint() {
        val k = KeyUploadFixture()
        val other = MatrixKeyFixture(k.keys.userId, k.keys.deviceId)
        val outcomes =
            race(listOf({ k.upload() }, { k.upload(CollaborationMatrixKeyUpload(other.device)) }))
        assertEquals(1, outcomes.count { it is Result.Success })
        assertEquals(
            listOf("collaboration_device_key_recovery_required"),
            outcomes.filterIsInstance<Result.Failed>().map { it.failure.code },
        )
        assertEquals(1, k.provider.uploads.get())
        assertEquals(1L, k.publication()?.version)
        assertTrue(k.provider.stored == k.keys.device || k.provider.stored == other.device)
    }

    @Test
    fun gatewayKeyPublicationUsesActualSynapseAndPrivateCredentialWithFencedReadback() {
        val g = GatewayFixture()
        MatrixProviderFixture("10m", g.f.clock).use { provider ->
            provider.start()
            val tokens = success(g.bootstrap())
            val keys =
                MatrixKeyFixture(
                    "@${collaborationMatrixLocalpart(requireNotNull(g.f.actor.companyId), g.f.account)}:matrix.example.test",
                    collaborationMatrixDeviceId(g.device),
                )
            val repo =
                InternalCollaborationMatrixKeyRepository(
                    HttpMatrixClientDataSource(provider.client, provider.config, g.f.clock)
                )
            val command =
                g.f.matrixIdentity(
                    provider.repository,
                    serverName = "matrix.example.test",
                    providerKeys = repo,
                )
            val upload =
                CollaborationMatrixKeyUpload(
                    keys.device,
                    mapOf("signed_curve25519:first" to keys.signed()),
                    mapOf("signed_curve25519:fallback" to keys.signed(true)),
                )
            repeat(2) {
                val response =
                    success(command.uploadKeys(tokens.accessToken, UUID.randomUUID(), upload))
                assertEquals(1, response.oneTimeKeyCounts["signed_curve25519"])
            }
            val retained =
                success(
                    g.f.transactions.run(g.f.actor) {
                        g.f.deviceKeys.find(
                            requireNotNull(g.f.actor.companyId),
                            g.f.account,
                            g.device,
                        )
                    }
                )
            assertEquals(1L, retained?.version)
            assertNotEquals(tokens.accessToken, g.f.credential(g.device)?.accessToken)
            assertEquals(0L, g.f.credential(g.device)?.generation)
            val queried =
                success(
                    command.queryKeys(
                        tokens.accessToken,
                        UUID.randomUUID(),
                        CollaborationMatrixKeyQuery(mapOf(keys.userId to emptyList())),
                    )
                )
            assertEquals(
                mapOf(keys.userId to mapOf(keys.deviceId to keys.device)),
                queried.deviceKeys,
            )
        }
    }

    @Test
    fun peerKeyQueryExpandsOnlyCurrentConfirmedDevicesAndRetainsOfflineRecipients() {
        val q = KeyQueryFixture()
        sql.update(
            "update native_sessions set access_expires_at=now()-interval '1 hour',mfa_verified_at=null where id=?",
            q.peerParent,
        )
        val value = (q.read() as Result.Success).value.deviceKeys
        assertEquals(setOf(q.upload.keys.userId, q.peerKeys.userId), value.keys)
        assertEquals(
            q.peerKeys.device,
            value.getValue(q.peerKeys.userId).getValue(q.peerKeys.deviceId),
        )
        assertEquals(1, q.calls.get())
        assertTrue(requireNotNull(q.request).devices.values.all { it.size == 1 })
        assertEquals(1L, q.upload.publication()?.version)
    }

    @Test
    fun peerWithNoConfirmedKeyReturnsEmptyWithoutQueryingUnconfirmedProviderKeys() {
        val q = KeyQueryFixture(published = false)
        val result = q.read(CollaborationMatrixKeyQuery(mapOf(q.peerKeys.userId to emptyList())))
        assertEquals(
            Result.Success(
                CollaborationMatrixKeyQueryResult(mapOf(q.peerKeys.userId to emptyMap()))
            ),
            result,
        )
        assertEquals(0, q.calls.get())
        assertEquals(
            0L,
            sql.queryForObject(
                "select version from collaboration_device_key_publications where device_id=?",
                Long::class.java,
                q.peerDevice,
            ),
        )
    }

    @Test
    fun keysOfUnrelatedOrForeignAccountsNeverReachTheProvider() {
        val q = KeyQueryFixture(paired = false)
        assertEquals(
            Result.Success(
                CollaborationMatrixKeyQueryResult(mapOf(q.peerKeys.userId to emptyMap()))
            ),
            q.read(CollaborationMatrixKeyQuery(mapOf(q.peerKeys.userId to emptyList()))),
        )
        val foreign = q.peerKeys.userId.replace(":proof.invalid", ":foreign.invalid")
        assertEquals(
            FailureKind.VALIDATION,
            (q.read(CollaborationMatrixKeyQuery(mapOf(foreign to emptyList()))) as Result.Failed)
                .failure
                .kind,
        )
        assertEquals(0, q.calls.get())
    }

    @Test
    fun removedAndUnknownAccountsInABatchDoNotBlockCurrentRecipientsOrRevealKeys() {
        val q = KeyQueryFixture()
        sql.update(
            "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
            q.company,
            q.peer,
        )
        val unknown = "@${collaborationMatrixLocalpart(q.company, UUID.randomUUID())}:proof.invalid"
        val input =
            CollaborationMatrixKeyQuery(
                mapOf(
                    q.upload.keys.userId to emptyList(),
                    q.peerKeys.userId to emptyList(),
                    unknown to emptyList(),
                )
            )
        val result = (q.read(input) as Result.Success).value.deviceKeys
        assertEquals(setOf(q.upload.keys.userId, q.peerKeys.userId, unknown), result.keys)
        assertEquals(
            q.upload.keys.device,
            result.getValue(q.upload.keys.userId).getValue(q.upload.keys.deviceId),
        )
        assertTrue(result.getValue(q.peerKeys.userId).isEmpty())
        assertTrue(result.getValue(unknown).isEmpty())
        assertEquals(setOf(q.upload.keys.userId), requireNotNull(q.request).devices.keys)
    }

    @Test
    fun peerKeyQueryRejectsProviderSubstitutionMissingKeysAndLateProviderErrors() {
        val q = KeyQueryFixture()
        q.keys[q.peerKeys.userId to q.peerKeys.deviceId] =
            MatrixKeyFixture(q.peerKeys.userId, q.peerKeys.deviceId).device
        assertEquals(
            "collaboration_device_key_recovery_required",
            (q.read() as Result.Failed).failure.code,
        )
        q.keys.remove(q.peerKeys.userId to q.peerKeys.deviceId)
        assertEquals(
            "collaboration_provider_invalid_response",
            (q.read() as Result.Failed).failure.code,
        )
        q.keys[q.peerKeys.userId to q.peerKeys.deviceId] = q.peerKeys.device
        q.onQuery = {
            sql.update(
                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                q.company,
                q.peer,
            )
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_outage"))
        }
        assertEquals("collaboration_recipients_changed", (q.read() as Result.Failed).failure.code)
    }

    @Test
    fun peerDeviceRemovalAndNewEnrollmentDuringQueryInvalidateTheCapturedSet() {
        for (adding in listOf(false, true)) {
            val q = KeyQueryFixture()
            q.onQuery = { result ->
                if (adding) q.enrollPeer()
                else
                    sql.update(
                        "update collaboration_devices set state='REVOKED',revoked_at=now(),updated_at=now(),version=version+1 where company_id=? and id=?",
                        q.company,
                        q.peerDevice,
                    )
                Result.Success(result)
            }
            assertEquals(
                "collaboration_recipients_changed",
                (q.read() as Result.Failed).failure.code,
            )
            q.onQuery = null
            val admitted = (q.read() as Result.Success).value.deviceKeys.getValue(q.peerKeys.userId)
            assertEquals(if (adding) 2 else 0, admitted.size)
        }
    }

    @Test
    fun peerSecurityChangeDuringQueryInvalidatesKeysButKeepsOwnedKeyHistory() {
        val q = KeyQueryFixture()
        q.onQuery = { result ->
            sql.update(
                "update accounts set security_version=security_version+1,version=version+1 where id=?",
                q.peer,
            )
            Result.Success(result)
        }
        assertEquals("collaboration_recipients_changed", (q.read() as Result.Failed).failure.code)
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from collaboration_device_key_publications where device_id=? and version=1",
                Int::class.java,
                q.peerDevice,
            ),
        )
        q.onQuery = null
        assertTrue(
            (q.read() as Result.Success).value.deviceKeys.getValue(q.peerKeys.userId).isEmpty()
        )
    }

    @Test
    fun queryCrossingCallerRefreshOrItsDeadlineCannotPublishCapturedKeys() {
        for (refresh in listOf(false, true)) {
            val q = KeyQueryFixture()
            q.onQuery = { result ->
                if (refresh)
                    sql.update(
                        "update native_sessions set version=version+1 where id=?",
                        q.upload.g.parent,
                    )
                else q.upload.g.f.clock.at = q.upload.g.f.clock.at.plusSeconds(61)
                Result.Success(result)
            }
            assertEquals(
                "collaboration_admission_changed",
                (q.read() as Result.Failed).failure.code,
            )
        }
    }

    private class KeyQueryFixture(published: Boolean = true, paired: Boolean = true) {
        val upload = KeyUploadFixture()
        val company = requireNotNull(upload.g.f.actor.companyId)
        val peer = upload.g.f.peer("Fictional key recipient")
        val keys =
            java.util.concurrent.ConcurrentHashMap<
                Pair<String, String>,
                CollaborationMatrixDeviceKeys,
            >()
        val calls = AtomicInteger()
        var request: CollaborationMatrixKeyQuery? = null
        var onQuery:
            ((CollaborationMatrixKeyQueryResult) -> Result<CollaborationMatrixKeyQueryResult>)? =
            null
        val peerParent: UUID
        val peerDevice: UUID
        val peerKeys: MatrixKeyFixture
        private val provider =
            object : CollaborationMatrixKeyRepository by upload.provider {
                override fun deviceKeys(
                    request: CollaborationMatrixKeyQuery,
                    token: String,
                    deadline: Instant,
                ): Result<CollaborationMatrixKeyQueryResult> {
                    assertFalse(
                        org.springframework.transaction.support.TransactionSynchronizationManager
                            .isActualTransactionActive()
                    )
                    calls.incrementAndGet()
                    this@KeyQueryFixture.request = request
                    val result =
                        CollaborationMatrixKeyQueryResult(
                            request.devices.mapValues { (user, ids) ->
                                ids.mapNotNull { device ->
                                        keys[user to device]?.let { device to it }
                                    }
                                    .toMap()
                            }
                        )
                    return onQuery?.invoke(result) ?: Result.Success(result)
                }
            }
        private val command = upload.g.f.matrixIdentity(upload.matrix, providerKeys = provider)

        init {
            assertInstanceOf(Result.Success::class.java, upload.upload())
            keys[upload.keys.userId to upload.keys.deviceId] = upload.keys.device
            val enrolled = enrollPeer(published)
            peerParent = enrolled.first
            peerKeys = enrolled.second
            peerDevice =
                UUID.fromString(
                    peerKeys.deviceId.removePrefix("HRIS_").let {
                        "${it.substring(0, 8)}-${it.substring(8, 12)}-${it.substring(12, 16)}-${it.substring(16, 20)}-${it.substring(20)}"
                    }
                )
            if (paired) {
                val pair = listOf(upload.g.f.account, peer).sortedBy { it.toString() }
                sql.update(
                    "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,now())",
                    company,
                    UUID.randomUUID(),
                    pair[0],
                    pair[1],
                    upload.g.f.account,
                )
            }
        }

        fun enrollPeer(published: Boolean = true): Pair<UUID, MatrixKeyFixture> {
            val parent = upload.g.f.parent(peer)
            val device = UUID.randomUUID()
            val key =
                MatrixKeyFixture(
                    "@${collaborationMatrixLocalpart(company, peer)}:proof.invalid",
                    collaborationMatrixDeviceId(device),
                )
            val fingerprint =
                (validateCollaborationDeviceKeys(key.device, key.userId, key.deviceId)
                        as Result.Success)
                    .value
            sql.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional recipient device','PROVISIONING',0,now()-interval '1 minute',now()-interval '1 minute')",
                company,
                device,
                peer,
            )
            sql.update(
                "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
                company,
                device,
            )
            sql.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,now())",
                company,
                device,
                peer,
                parent,
            )
            sql.update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,?,0,now())",
                company,
                device,
                peer,
                fingerprint,
            )
            if (published)
                sql.update(
                    "update collaboration_device_key_publications set version=1,published_at=now() where company_id=? and device_id=?",
                    company,
                    device,
                )
            keys[key.userId to key.deviceId] = key.device
            return parent to key
        }

        fun read(
            input: CollaborationMatrixKeyQuery =
                CollaborationMatrixKeyQuery(
                    mapOf(upload.keys.userId to emptyList(), peerKeys.userId to emptyList())
                )
        ) = command.queryKeys(upload.tokens.accessToken, UUID.randomUUID(), input)
    }

    private class KeyUploadFixture {
        val g = GatewayFixture()
        val keys =
            MatrixKeyFixture(
                "@${collaborationMatrixLocalpart(requireNotNull(g.f.actor.companyId), g.f.account)}:proof.invalid",
                collaborationMatrixDeviceId(g.device),
            )
        val matrix = RecordingMatrix(g.f.clock)
        val provider = RecordingKeys()
        val tokens = (g.bootstrap() as Result.Success).value
        val command = g.f.matrixIdentity(matrix, providerKeys = provider)
        val request =
            CollaborationMatrixKeyUpload(
                keys.device,
                mapOf("signed_curve25519:first" to keys.signed()),
                mapOf("signed_curve25519:fallback" to keys.signed(true)),
            )

        fun upload(value: CollaborationMatrixKeyUpload = request) =
            command.uploadKeys(tokens.accessToken, UUID.randomUUID(), value)

        fun publication() =
            (g.f.transactions.run(g.f.actor) {
                    g.f.deviceKeys.find(requireNotNull(g.f.actor.companyId), g.f.account, g.device)
                } as Result.Success)
                .value
    }

    private class RecordingKeys : CollaborationMatrixKeyRepository {
        override fun deviceKeys(
            request: CollaborationMatrixKeyQuery,
            token: String,
            deadline: Instant,
        ): Result<CollaborationMatrixKeyQueryResult> = error("Own-device publication fixture")

        val queries = AtomicInteger()
        val uploads = AtomicInteger()
        @Volatile var stored: CollaborationMatrixDeviceKeys? = null
        @Volatile var onQuery: (() -> Result<CollaborationMatrixDeviceKeys?>)? = null
        @Volatile
        var onUpload: ((CollaborationMatrixKeyUpload) -> Result<CollaborationMatrixKeyCounts>)? =
            null
        private val ids = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        override fun ownDeviceKeys(
            userId: String,
            deviceId: String,
            token: String,
            deadline: Instant,
        ): Result<CollaborationMatrixDeviceKeys?> {
            assertFalse(
                org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()
            )
            queries.incrementAndGet()
            return onQuery?.invoke() ?: Result.Success(stored)
        }

        override fun upload(
            request: CollaborationMatrixKeyUpload,
            token: String,
            deadline: Instant,
        ): Result<CollaborationMatrixKeyCounts> {
            assertFalse(
                org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()
            )
            uploads.incrementAndGet()
            return onUpload?.invoke(request) ?: accept(request)
        }

        fun accept(request: CollaborationMatrixKeyUpload): Result<CollaborationMatrixKeyCounts> {
            request.deviceKeys?.let { stored = it }
            ids.addAll(request.oneTimeKeys.keys)
            return Result.Success(
                CollaborationMatrixKeyCounts(mapOf("signed_curve25519" to ids.size))
            )
        }
    }

    @Test
    fun gatewayIdentityReachesActualSynapseWithoutExposingItsBearer() {
        val g = GatewayFixture()
        MatrixProviderFixture("10m", g.f.clock).use { provider ->
            provider.start()
            val tokens = success(g.bootstrap())
            val request =
                g.f.matrixIdentity(provider.repository, serverName = "matrix.example.test")
            val identity = success(request.identity(tokens.accessToken, UUID.randomUUID()))
            assertEquals(
                "@${collaborationMatrixLocalpart(requireNotNull(g.f.actor.companyId), g.f.account)}:matrix.example.test",
                identity.userId,
            )
            assertEquals(collaborationMatrixDeviceId(g.device), identity.deviceId)
            assertFalse(identity.isGuest)
            val stored = requireNotNull(g.f.credential(g.device))
            assertFalse(stored.accessToken == tokens.accessToken)
            assertEquals(0L, stored.generation)
            assertEquals(identity, success(request.identity(tokens.accessToken, UUID.randomUUID())))
            assertEquals(stored.accessToken, g.f.credential(g.device)?.accessToken)
        }
    }

    @Test
    fun gatewayIdentityRenewsOnlyInternalProviderBearerAndKeepsDeviceAndPublicSession() {
        val g = GatewayFixture()
        val matrix = RecordingMatrix(g.f.clock)
        val request = g.f.matrixIdentity(matrix)
        val tokens = success(g.bootstrap())
        val first = success(request.identity(tokens.accessToken, UUID.randomUUID()))
        assertEquals(first, success(request.identity(tokens.accessToken, UUID.randomUUID())))
        assertEquals(1, matrix.logins.get())
        val prior = requireNotNull(g.f.credential(g.device))
        g.f.clock.at = g.f.clock.at.plusSeconds(280)
        val next = success(g.refresh(tokens.refreshToken))
        g.f.clock.at = g.f.clock.at.plusSeconds(20)
        assertEquals(first, success(request.identity(next.accessToken, UUID.randomUUID())))
        val renewed = requireNotNull(g.f.credential(g.device))
        assertEquals(prior.generation + 1, renewed.generation)
        assertNotEquals(prior.accessToken, renewed.accessToken)
        assertEquals(prior.createdAt, renewed.createdAt)
        assertEquals(next.version, g.f.gatewayHead(g.device).version)
        assertEquals(2, matrix.logins.get())
        assertEquals(0, matrix.logouts.get())
    }

    @Test
    fun gatewayIdentityRechecksPermissionAfterLoginAndNeverStoresTheLosingToken() {
        val g = GatewayFixture()
        val tokens = success(g.bootstrap())
        val matrix = RecordingMatrix(g.f.clock)
        matrix.onLogin = {
            sql.update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                g.f.actor.companyId,
                g.f.account,
            )
            matrix.issue(it)
        }
        assertEquals(
            "access_denied",
            failed(g.f.matrixIdentity(matrix).identity(tokens.accessToken, UUID.randomUUID())),
        )
        assertNull(g.f.credential(g.device))
        assertEquals(0, matrix.queries.get())
        assertEquals(0, matrix.logouts.get())
        assertEquals(0L, g.f.gatewayHead(g.device).version)
    }

    @Test
    fun gatewayIdentityDoesNotPublishAcrossConcurrentPublicRefresh() {
        val g = GatewayFixture()
        val tokens = success(g.bootstrap())
        g.f.clock.at = g.f.clock.at.plusSeconds(10)
        val matrix = RecordingMatrix(g.f.clock)
        matrix.onLogin = {
            success(g.refresh(tokens.refreshToken))
            matrix.issue(it)
        }
        assertEquals(
            "collaboration_gateway_authentication_required",
            failed(g.f.matrixIdentity(matrix).identity(tokens.accessToken, UUID.randomUUID())),
        )
        assertNull(g.f.credential(g.device))
        assertEquals(1L, g.f.gatewayHead(g.device).version)
        assertEquals(0, matrix.logouts.get())
    }

    @Test
    fun gatewayIdentityCompetingRenewalsKeepOneGenerationWithoutLoggingOutWinner() {
        val g = GatewayFixture()
        val tokens = success(g.bootstrap())
        val matrix = RecordingMatrix(g.f.clock)
        val ready = CyclicBarrier(2)
        matrix.onLogin = {
            ready.await(10, TimeUnit.SECONDS)
            matrix.issue(it)
        }
        val request = g.f.matrixIdentity(matrix)
        val answers = race(List(2) { { request.identity(tokens.accessToken, UUID.randomUUID()) } })
        assertEquals(success(answers[0]), success(answers[1]))
        assertEquals(2, matrix.logins.get())
        assertEquals(0L, g.f.credential(g.device)?.generation)
        assertEquals(0, matrix.logouts.get())
        assertEquals(2, matrix.queries.get())
    }

    @Test
    fun gatewayIdentityWithholdsProviderResultAfterNativeRefreshOrRevocationAndPermissionLoss() {
        for (change in listOf("refresh", "revoke", "permission", "expiry", "module")) {
            val g = GatewayFixture()
            val tokens = success(g.bootstrap())
            val matrix = RecordingMatrix(g.f.clock)
            matrix.onQuery = { token ->
                when (change) {
                    "refresh" ->
                        sql.update(
                            "update native_sessions set version=version+1,rotated_at=now() where id=?",
                            g.parent,
                        )
                    "revoke" ->
                        sql.update(
                            "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                            g.parent,
                        )
                    "permission" ->
                        sql.update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                            g.f.actor.companyId,
                            g.f.account,
                        )
                    "expiry" -> g.f.clock.at = tokens.accessExpiresAt
                    "module" ->
                        g.f.policy(
                            ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)),
                            g.f.clock.at,
                        )
                }
                Result.Success(requireNotNull(matrix.issued[token]))
            }
            val request = g.f.matrixIdentity(matrix)
            val denied = request.identity(tokens.accessToken, UUID.randomUUID())
            assertInstanceOf(Result.Failed::class.java, denied, change)
            if (change == "refresh") {
                assertEquals("collaboration_admission_changed", failed(denied))
                assertEquals(FailureKind.UNAVAILABLE, (denied as Result.Failed).failure.kind)
                matrix.onQuery = { Result.Success(requireNotNull(matrix.issued[it])) }
                assertInstanceOf(
                    Result.Success::class.java,
                    request.identity(tokens.accessToken, UUID.randomUUID()),
                )
            }
            assertNotNull(g.f.credential(g.device))
            assertEquals(0, matrix.logouts.get())
        }
    }

    @Test
    fun gatewayIdentityRollsBackCredentialAndJournalOnLateScheduledPolicyBoundary() {
        val g = GatewayFixture()
        val tokens = success(g.bootstrap())
        val matrix = RecordingMatrix(g.f.clock)
        val boundary = g.f.clock.at.plusSeconds(10)
        g.f.policy(ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)), boundary)
        val journal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    g.f.journal.record(actor, change).also { g.f.clock.at = boundary }
            }
        val before = g.f.rows("audit_entries")
        assertInstanceOf(
            Result.Failed::class.java,
            g.f.matrixIdentity(matrix, journal).identity(tokens.accessToken, UUID.randomUUID()),
        )
        assertNull(g.f.credential(g.device))
        assertEquals(before, g.f.rows("audit_entries"))
        assertEquals(0, matrix.queries.get())
        assertEquals(0, matrix.logouts.get())
    }

    @Test
    fun gatewayIdentityRejectsWrongProviderIdentityAndStaleOrWrongTransportTokens() {
        val g = GatewayFixture()
        val tokens = success(g.bootstrap())
        val matrix = RecordingMatrix(g.f.clock)
        val request = g.f.matrixIdentity(matrix)
        for (token in
            listOf("invalid", tokens.refreshToken, "hris_mx_a_" + "a".repeat(43))) assertEquals(
            "collaboration_gateway_authentication_required",
            failed(request.identity(token, UUID.randomUUID())),
        )
        assertEquals(0, matrix.logins.get())
        matrix.onQuery = {
            Result.Success(CollaborationMatrixIdentity("@foreign:proof.invalid", "foreign", false))
        }
        assertEquals(
            "collaboration_provider_invalid_response",
            failed(request.identity(tokens.accessToken, UUID.randomUUID())),
        )
        g.f.clock.at = tokens.accessExpiresAt
        assertEquals(
            "collaboration_gateway_authentication_required",
            failed(request.identity(tokens.accessToken, UUID.randomUUID())),
        )
        assertEquals(1, matrix.queries.get())
    }

    @Test
    fun gatewayIdentityReportsAdmissionRemovalBeforeProviderFailureAndPreservesCancellation() {
        for (duringLogin in listOf(true, false)) {
            val g = GatewayFixture()
            val tokens = success(g.bootstrap())
            val matrix = RecordingMatrix(g.f.clock)
            val removed = {
                sql.update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                    g.f.actor.companyId,
                    g.f.account,
                )
                Result.Failed(
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable")
                )
            }
            if (duringLogin) matrix.onLogin = { removed() } else matrix.onQuery = { removed() }
            assertEquals(
                "access_denied",
                failed(g.f.matrixIdentity(matrix).identity(tokens.accessToken, UUID.randomUUID())),
            )
            assertEquals(if (duringLogin) 0 else 1, g.f.rows("collaboration_provider_sessions"))
            assertEquals(0, matrix.logouts.get())
        }
        val g = GatewayFixture()
        val tokens = success(g.bootstrap())
        val matrix = RecordingMatrix(g.f.clock)
        matrix.onLogin = { throw InterruptedException() }
        assertThrows(InterruptedException::class.java) {
            g.f.matrixIdentity(matrix).identity(tokens.accessToken, UUID.randomUUID())
        }
        assertNull(g.f.credential(g.device))
        assertEquals(0, matrix.queries.get())
    }

    @Test
    fun participantsAreOnlyEligibleCompanyPeersAndPaginationExcludesSelfBeforeLimiting() {
        val f = Fixture()
        val parent = f.parent()
        val expected = List(3) { f.peer("Fictional peer $it") }.sortedBy(UUID::toString)
        f.peer("Fictional no permission", grant = false)
        f.peer("Fictional inactive account", active = false)
        f.peer("Fictional inactive membership", memberActive = false)
        f.peer("Fictional other company", company = f.company())
        var after: UUID? = null
        val seen = mutableListOf<UUID>()
        repeat(3) { index ->
            val page =
                success(
                    f.participants()
                        .execute(
                            f.actor,
                            parent,
                            0,
                            ClientVersion(ClientPlatform.ANDROID, 1),
                            " Fictional ",
                            after,
                            1,
                        )
                )
            assertEquals(1, page.items.size)
            seen.add(page.items.single().accountId)
            assertEquals(expected[index], seen.last())
            assertTrue(page.items.single().displayName.startsWith("Fictional peer"))
            if (index == 2) assertNull(page.nextCursor)
            else assertEquals(seen.last().toString(), page.nextCursor)
            after = page.nextCursor?.let(UUID::fromString)
        }
        assertEquals(expected, seen)
        assertFalse(f.account in seen)
        assertEquals(0, f.rows("collaboration_devices"))
        assertEquals(0, f.rows("background_jobs"))
    }

    @Test
    fun participantSearchUsesLiteralTextAndBoundsWithoutRequiringProviderEnrollment() {
        val f = Fixture()
        val parent = f.parent()
        val matching = f.peer("Fictional 100% peer")
        f.peer("Fictional 100X peer")
        val request = f.participants()
        val client = ClientVersion(ClientPlatform.ANDROID, 1)
        assertEquals(
            listOf(matching),
            success(request.execute(f.actor, parent, 0, client, "%", null, 50)).items.map {
                it.accountId
            },
        )
        for ((query, limit) in
            listOf("" to 0, "" to 51, "a".repeat(81) to 30, "bad\nquery" to 30)) assertEquals(
            "invalid_collaboration_participant_query",
            failed(request.execute(f.actor, parent, 0, client, query, null, limit)),
        )
        for (invalid in
            listOf(
                null,
                ClientVersion(ClientPlatform.WEB, 1),
                ClientVersion(ClientPlatform.ANDROID, 0),
            )) assertEquals(
            "invalid_client_version",
            failed(request.execute(f.actor, parent, 0, invalid, "", null, 30)),
        )
        assertEquals(
            "collaboration_unavailable",
            failed(
                f.participants(provider = CollaborationMatrixPolicy(false, ""))
                    .execute(f.actor, parent, 0, client, "", null, 30)
            ),
        )
    }

    @Test
    fun participantReadWithholdsAcquiredPageWhenAccessMfaOrScheduledPolicyExpires() {
        for (mode in listOf("access", "mfa", "policy")) {
            val f = Fixture()
            val parent = f.parent()
            f.peer("Fictional peer")
            val boundary = f.clock.at.plusSeconds(2)
            val security =
                IdentitySecurityPolicy(
                    enforceMfa = mode == "mfa",
                    maximumMfaAge = Duration.ofSeconds(2),
                    recentAuthenticationAge = Duration.ofSeconds(1),
                )
            val actor = if (mode == "mfa") f.actor.copy(mfaVerifiedAt = f.clock.at) else f.actor
            when (mode) {
                "access" ->
                    sql.update(
                        "update native_sessions set access_expires_at=? where id=?",
                        boundary.atOffset(ZoneOffset.UTC),
                        parent,
                    )
                "policy" ->
                    f.policy(
                        ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)),
                        boundary,
                    )
                "mfa" -> {
                    sql.update(
                        "update accounts set mfa_secret_encrypted='fictional-enrolled' where id=?",
                        f.account,
                    )
                    sql.update(
                        "update native_sessions set mfa_verified_at=? where id=?",
                        f.clock.at.atOffset(ZoneOffset.UTC),
                        parent,
                    )
                }
            }
            val members =
                object : MembershipRepository by f.members {
                    override fun activeReferences(
                        companyId: UUID,
                        permissions: Set<String>,
                        query: String,
                        after: UUID?,
                        limit: Int,
                        excludedAccountId: UUID?,
                    ) =
                        f.members
                            .activeReferences(
                                companyId,
                                permissions,
                                query,
                                after,
                                limit,
                                excludedAccountId,
                            )
                            .also { f.clock.at = boundary }
                }
            val result =
                f.participants(members, security)
                    .execute(
                        actor,
                        parent,
                        0,
                        ClientVersion(ClientPlatform.ANDROID, 1),
                        "",
                        null,
                        30,
                    )
            assertEquals(
                when (mode) {
                    "mfa" -> "mfa_required"
                    "policy" -> "company_module_disabled"
                    else -> "native_session_invalid"
                },
                failed(result),
            )
        }
    }

    @Test
    fun participantDiscoveryRejectsRevokedOrReplacedNativeParentsBeforeReadingPeers() {
        for (revoke in listOf(true, false)) {
            val f = Fixture()
            val parent = f.parent()
            if (revoke)
                sql.update(
                    "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                    parent,
                )
            else sql.update("update native_sessions set version=version+1 where id=?", parent)
            val members =
                object : MembershipRepository by f.members {
                    override fun activeReferences(
                        companyId: UUID,
                        permissions: Set<String>,
                        query: String,
                        after: UUID?,
                        limit: Int,
                        excludedAccountId: UUID?,
                    ): Result<Page<dev.fajar.hris.identity.domain.entities.MemberReference>> =
                        error("Denied parent must not acquire participant names")
                }
            assertEquals(
                "native_session_invalid",
                failed(
                    f.participants(members)
                        .execute(
                            f.actor,
                            parent,
                            0,
                            ClientVersion(ClientPlatform.ANDROID, 1),
                            "",
                            null,
                            30,
                        )
                ),
            )
        }
    }

    @Test
    fun directOppositeOpenAndLostResponseReplayKeepOneCanonicalPair() {
        val f = Fixture(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
        val peer = f.peer("Fictional direct peer")
        val parent = f.parent()
        val peerParent = f.parent(peer)
        val peerActor = f.actor.copy(accountId = peer)
        val operation = UUID.randomUUID()
        val client = ClientVersion(ClientPlatform.ANDROID, 1)
        val outcomes =
            race(
                    listOf(
                        { f.openDirect().execute(f.actor, parent, 0, client, operation, peer) },
                        {
                            f.openDirect()
                                .execute(
                                    peerActor,
                                    peerParent,
                                    0,
                                    client,
                                    UUID.randomUUID(),
                                    f.account,
                                )
                        },
                    )
                )
                .map { success(it) }
        assertEquals(outcomes[0].id, outcomes[1].id)
        assertEquals(1, f.rows("collaboration_direct_conversations"))
        assertEquals(2, f.rows("operation_receipts"))
        assertEquals(1, f.rows("audit_entries"))
        assertEquals(1, f.rows("outbox_events"))
        assertEquals(0, f.rows("collaboration_devices"))
        assertEquals(0, f.rows("background_jobs"))
        val first = success(f.readDirect().get(f.actor, parent, 0, client, outcomes[0].id))
        assertEquals(peer, first.firstAccountId)
        assertEquals(f.account, first.secondAccountId)
        assertEquals(first, success(f.readDirect().get(peerActor, peerParent, 0, client, first.id)))
        sql.update("update native_sessions set version=version+1 where id=?", parent)
        val replay = success(f.openDirect().execute(f.actor, parent, 1, client, operation, peer))
        assertEquals(first.id, replay.id)
        assertTrue(replay.replayed)
        assertEquals(
            "operation_payload_mismatch",
            failed(
                f.openDirect()
                    .execute(f.actor, parent, 1, client, operation, f.peer("Different peer"))
            ),
        )
        sql.update("update native_sessions set revoked_at=now() where id=?", parent)
        assertEquals(
            "native_session_invalid",
            failed(f.openDirect().execute(f.actor, parent, 1, client, operation, peer)),
        )
    }

    @Test
    fun directAccountGuardsDoNotInvertConcurrentAdministrationWriterOrder() {
        val f = Fixture(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"))
        // Ensure one positive and one negative Java UUID most-significant long.
        val peer =
            f.peer(
                "Fictional ordered peer",
                id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            )
        val parent = f.parent()
        val first = listOf(f.account, peer).sorted().first()
        val second = listOf(f.account, peer).sorted().last()
        val held = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val attempts = AtomicInteger()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val writer =
                executor.submit<Result<Unit>> {
                    f.transactions.run(f.actor) {
                        f.identities.lockAccount(first).flatMap {
                            held.countDown()
                            check(entered.await(4, TimeUnit.SECONDS))
                            f.identities.lockAccount(second)
                        }
                    }
                }
            assertTrue(held.await(4, TimeUnit.SECONDS))
            val observer =
                object : IdentityRepository by f.identities {
                    override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> {
                        if (attempts.getAndIncrement() == 0) {
                            if (accountId == first) entered.countDown()
                            else
                                return f.identities.lockAccount(accountId, shared).also {
                                    entered.countDown()
                                }
                        }
                        return f.identities.lockAccount(accountId, shared)
                    }
                }
            val candidate =
                executor.submit<Result<MutationReceipt>> {
                    f.openDirect(identities = observer)
                        .execute(
                            f.actor,
                            parent,
                            0,
                            ClientVersion(ClientPlatform.ANDROID, 1),
                            UUID.randomUUID(),
                            peer,
                        )
                }
            success(writer.get(10, TimeUnit.SECONDS))
            success(candidate.get(10, TimeUnit.SECONDS))
            assertEquals(1, f.rows("collaboration_direct_conversations"))
        } finally {
            entered.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun directRlsHidesOtherPairsAndCompaniesAndRetainsIdentity() {
        val f = Fixture()
        val peer = f.peer("Fictional peer")
        val third = f.peer("Fictional unrelated participant")
        val client = ClientVersion(ClientPlatform.ANDROID, 1)
        val parent = f.parent()
        val receipt =
            success(f.openDirect().execute(f.actor, parent, 0, client, UUID.randomUUID(), peer))
        val company = requireNotNull(f.actor.companyId)
        val stored =
            requireNotNull(
                success(f.transactions.run(f.actor) { f.direct.find(company, receipt.id) })
            )
        assertNull(
            success(
                f.transactions.run(f.actor.copy(accountId = third)) {
                    f.direct.find(company, receipt.id)
                }
            )
        )
        assertNull(
            success(
                f.transactions.run(f.actor.copy(companyId = UUID.randomUUID())) {
                    f.direct.find(company, receipt.id)
                }
            )
        )
        val raw = JdbcTemplate(runtime)
        for (actor in listOf(f.actor, f.actor.copy(accountId = peer))) {
            assertEquals(
                0,
                success(
                    f.transactions.run(actor) {
                        Result.Success(
                            raw.update(
                                "update collaboration_direct_conversations set version=0 where id=?",
                                receipt.id,
                            )
                        )
                    }
                ),
            )
            assertEquals(
                0,
                success(
                    f.transactions.run(actor) {
                        Result.Success(
                            raw.update(
                                "delete from collaboration_direct_conversations where id=?",
                                receipt.id,
                            )
                        )
                    }
                ),
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            f.transactions.run(f.actor.copy(accountId = third)) {
                f.direct.create(stored.copy(id = UUID.randomUUID()))
            },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.transactions.run(f.actor) {
                f.direct.create(stored.copy(id = UUID.randomUUID(), companyId = UUID.randomUUID()))
            },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.transactions.run(f.actor) {
                f.direct.create(
                    stored.copy(
                        id = UUID.randomUUID(),
                        firstAccountId = stored.secondAccountId,
                        secondAccountId = stored.firstAccountId,
                    )
                )
            },
        )
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            sql.update(
                "update collaboration_direct_conversations set created_by=? where id=?",
                third,
                receipt.id,
            )
        }
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            sql.update("delete from collaboration_direct_conversations where id=?", receipt.id)
        }
        assertEquals(1, f.rows("collaboration_direct_conversations"))
        assertEquals(
            0,
            raw.queryForObject(
                "select count(*) from collaboration_direct_conversations",
                Int::class.java,
            ),
        )
    }

    @Test
    fun directSelectedPeerIsReauthorizedEvenForReceiptReplay() {
        val f = Fixture()
        val client = ClientVersion(ClientPlatform.ANDROID, 1)
        val parent = f.parent()
        val peer = f.peer("Fictional enabled peer")
        val operation = UUID.randomUUID()
        success(f.openDirect().execute(f.actor, parent, 0, client, operation, peer))
        sql.update(
            "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
            f.actor.companyId,
            peer,
        )
        val rejected =
            listOf(
                peer,
                f.peer("No grant", grant = false),
                f.peer("Inactive", active = false),
                f.peer("Inactive membership", memberActive = false),
                f.peer("Foreign", company = f.company()),
                UUID.randomUUID(),
            )
        for (candidate in rejected) assertEquals(
            "collaboration_peer_unavailable",
            failed(f.openDirect().execute(f.actor, parent, 0, client, UUID.randomUUID(), candidate)),
        )
        assertEquals(
            "collaboration_peer_unavailable",
            failed(f.openDirect().execute(f.actor, parent, 0, client, operation, peer)),
        )
        assertEquals(
            "invalid_collaboration_peer",
            failed(f.openDirect().execute(f.actor, parent, 0, client, UUID.randomUUID(), f.account)),
        )
        assertEquals(
            "invalid_client_version",
            failed(f.openDirect().execute(f.actor, parent, 0, null, UUID.randomUUID(), peer)),
        )
        assertEquals(1, f.rows("collaboration_direct_conversations"))
        assertEquals(1, f.rows("operation_receipts"))
    }

    @Test
    fun directJournalFailureExpiryAndCancellationRollBackIntentAndReceiptTogether() {
        for (mode in listOf("failure", "expiry", "interrupt")) {
            val f = Fixture()
            val peer = f.peer("Fictional peer")
            val parent = f.parent()
            val journal =
                object : ChangeJournalRepository by f.journal {
                    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
                        val written = f.journal.record(actor, change)
                        if (mode == "interrupt")
                            throw InterruptedException("Owned fixture cancellation")
                        if (mode == "expiry") {
                            f.clock.at = f.clock.at.plusSeconds(1000)
                            return written
                        }
                        return Result.Failed(
                            Failure(FailureKind.UNAVAILABLE, "fixture_journal_failed")
                        )
                    }
                }
            val command = {
                f.openDirect(journal = journal)
                    .execute(
                        f.actor,
                        parent,
                        0,
                        ClientVersion(ClientPlatform.ANDROID, 1),
                        UUID.randomUUID(),
                        peer,
                    )
            }
            if (mode == "interrupt") assertThrows(InterruptedException::class.java) { command() }
            else
                assertEquals(
                    if (mode == "expiry") "native_session_invalid" else "fixture_journal_failed",
                    failed(command()),
                )
            for (table in
                listOf(
                    "collaboration_direct_conversations",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                )) assertEquals(0, f.rows(table), table)
        }
    }

    @Test
    fun directReadPaginationAndOffboardedPeerRetainOnlyOwnedMetadata() {
        val f = Fixture()
        val parent = f.parent()
        val client = ClientVersion(ClientPlatform.ANDROID, 1)
        val peers = List(3) { f.peer("Fictional peer $it") }
        val ids =
            peers
                .map {
                    success(
                            f.openDirect()
                                .execute(f.actor, parent, 0, client, UUID.randomUUID(), it)
                        )
                        .id
                }
                .sortedBy(UUID::toString)
        var cursor: UUID? = null
        val seen = mutableListOf<UUID>()
        repeat(3) {
            val page = success(f.readDirect().list(f.actor, parent, 0, client, cursor, 1))
            assertEquals(1, page.items.size)
            seen += page.items.single().id
            cursor = page.nextCursor?.let(UUID::fromString)
            assertEquals(it < 2, cursor != null)
        }
        assertEquals(ids, seen)
        assertEquals(
            "invalid_collaboration_conversation_query",
            failed(f.readDirect().list(f.actor, parent, 0, client, null, 51)),
        )
        val unrelated = f.peer("Unrelated peer")
        assertEquals(
            "collaboration_conversation_not_found",
            failed(
                f.readDirect()
                    .get(
                        f.actor.copy(accountId = unrelated),
                        f.parent(unrelated),
                        0,
                        client,
                        ids.first(),
                    )
            ),
        )
        sql.update("update accounts set active=false where id=?", peers.first())
        assertEquals(
            3,
            success(f.readDirect().list(f.actor, parent, 0, client, null, 30)).items.size,
        )
        sql.update(
            "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
            f.actor.companyId,
            f.account,
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.readDirect().get(f.actor, parent, 0, client, ids.first()),
        )
        assertEquals(3, f.rows("collaboration_direct_conversations"))
    }

    @Test
    fun directAcquiredMetadataIsWithheldWhenNativeAccessExpires() {
        for (single in listOf(false, true)) {
            val f = Fixture()
            val parent = f.parent()
            val client = ClientVersion(ClientPlatform.ANDROID, 1)
            val peer = f.peer("Fictional peer")
            val id =
                success(f.openDirect().execute(f.actor, parent, 0, client, UUID.randomUUID(), peer))
                    .id
            val late =
                object : CollaborationDirectRepository by f.direct {
                    override fun find(
                        companyId: UUID,
                        id: UUID,
                    ): Result<CollaborationDirectConversation?> =
                        f.direct.find(companyId, id).also {
                            f.clock.at = f.clock.at.plusSeconds(1000)
                        }

                    override fun list(
                        companyId: UUID,
                        accountId: UUID,
                        after: UUID?,
                        limit: Int,
                    ): Result<Page<CollaborationDirectConversation>> =
                        f.direct.list(companyId, accountId, after, limit).also {
                            f.clock.at = f.clock.at.plusSeconds(1000)
                        }
                }
            val reader = f.readDirect(late)
            assertEquals(
                "native_session_invalid",
                failed(
                    if (single) reader.get(f.actor, parent, 0, client, id)
                    else reader.list(f.actor, parent, 0, client, null, 30)
                ),
            )
            assertEquals(1, f.rows("collaboration_direct_conversations"))
        }
    }

    @Test
    fun directWaitingCommandRechecksAuthorityAfterAcquiringPairGuard() {
        val f = Fixture()
        val parent = f.parent()
        val peer = f.peer("Fictional peer")
        val pair = listOf(f.account, peer).sortedBy(UUID::toString)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requested = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val holder =
                executor.submit<Result<Unit>> {
                    f.transactions.run(f.actor) {
                        f.direct
                            .lockPair(requireNotNull(f.actor.companyId), pair[0], pair[1])
                            .flatMap {
                                held.countDown()
                                check(release.await(4, TimeUnit.SECONDS))
                                Result.Success(Unit)
                            }
                    }
                }
            assertTrue(held.await(4, TimeUnit.SECONDS))
            val observer =
                object : CollaborationDirectRepository by f.direct {
                    override fun lockPair(
                        companyId: UUID,
                        first: UUID,
                        second: UUID,
                    ): Result<Unit> {
                        requested.countDown()
                        return f.direct.lockPair(companyId, first, second)
                    }
                }
            val candidate =
                executor.submit<Result<MutationReceipt>> {
                    f.openDirect(observer)
                        .execute(
                            f.actor,
                            parent,
                            0,
                            ClientVersion(ClientPlatform.ANDROID, 1),
                            UUID.randomUUID(),
                            peer,
                        )
                }
            assertTrue(requested.await(4, TimeUnit.SECONDS))
            f.clock.at = f.clock.at.plusSeconds(1000)
            release.countDown()
            success(holder.get(5, TimeUnit.SECONDS))
            assertEquals("native_session_invalid", failed(candidate.get(5, TimeUnit.SECONDS)))
            assertEquals(0, f.rows("collaboration_direct_conversations"))
            assertEquals(0, f.rows("operation_receipts"))
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun directRoomRequestReplaysWithoutDuplicateJobAndRechecksPeer() {
        val room = DirectRoomFixture()
        val f = room.f
        assertEquals(CollaborationDirectRoomState.NOT_REQUESTED, success(room.status()).state)
        val receipt = success(room.request())
        assertEquals(MutationReceipt(room.conversation, 0), receipt)
        assertEquals(receipt.copy(replayed = true), success(room.request()))
        assertEquals(receipt, success(room.request(operation = UUID.randomUUID())))
        assertEquals(2, f.rows("background_jobs"))
        assertEquals(1, f.rows("collaboration_direct_room_provisioning"))
        val status = success(room.status())
        assertEquals(CollaborationDirectRoomState.QUEUED, status.state)
        assertNull(status.matrixRoomId)
        assertFalse(status.retryable)
        sql.update(
            "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
            f.actor.companyId,
            room.peer,
        )
        assertEquals("collaboration_peer_unavailable", failed(room.request()))
        assertEquals(CollaborationDirectRoomState.QUEUED, success(room.status()).state)
        assertEquals(2, f.rows("background_jobs"))
    }

    @Test
    fun directRoomRequestRejectsWrongDeviceVersionAndMissingAuthorityWithoutEnqueue() {
        val room = DirectRoomFixture()
        val f = room.f
        assertEquals("collaboration_room_provisioning_changed", failed(room.request(version = 0)))
        assertEquals(
            "collaboration_access_denied",
            failed(room.request(device = UUID.randomUUID())),
        )
        assertEquals("invalid_client_version", failed(room.request(client = null)))
        assertEquals(
            "collaboration_conversation_not_found",
            failed(room.request(conversation = UUID.randomUUID())),
        )
        assertEquals(
            "collaboration_unavailable",
            failed(
                room.request(
                    command = f.requestRoom(provider = CollaborationMatrixPolicy(false, ""))
                )
            ),
        )
        assertEquals(0, f.rows("collaboration_direct_room_provisioning"))
        assertEquals(1, f.rows("background_jobs"))
        sql.update("update native_sessions set revoked_at=now() where id=?", room.parent)
        assertEquals("native_session_invalid", failed(room.request()))
    }

    @Test
    fun directRoomCompetingRequestsUseOnePointerAndPeerCannotTakeOverRetry() {
        val room = DirectRoomFixture()
        val f = room.f
        val peerActor = f.actor.copy(accountId = room.peer)
        val peerParent = f.parent(room.peer)
        val peerDevice = success(f.register(peerParent, actor = peerActor)).id
        f.activate(peerDevice)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first =
                executor.submit<Result<MutationReceipt>> {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    room.request()
                }
            val second =
                executor.submit<Result<MutationReceipt>> {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    f.requestRoom()
                        .execute(
                            peerActor,
                            peerParent,
                            0,
                            room.client,
                            UUID.randomUUID(),
                            room.conversation,
                            peerDevice,
                            1,
                            null,
                        )
                }
            start.countDown()
            assertEquals(
                success(first.get(15, TimeUnit.SECONDS)),
                success(second.get(15, TimeUnit.SECONDS)),
            )
            assertEquals(3, f.rows("background_jobs"))
            val pointer = room.pointer()
            sql.update(
                "update background_jobs set status='FAILED',finished_at=now() where id=?",
                pointer.jobId,
            )
            f.clock.at = f.clock.at.plusSeconds(61)
            val otherActor = if (pointer.accountId == f.account) peerActor else f.actor
            val otherParent = if (otherActor == peerActor) peerParent else room.parent
            val otherDevice = if (otherActor == peerActor) peerDevice else room.device
            assertEquals(
                "collaboration_room_creator_required",
                failed(
                    f.requestRoom()
                        .execute(
                            otherActor,
                            otherParent,
                            0,
                            room.client,
                            UUID.randomUUID(),
                            room.conversation,
                            otherDevice,
                            1,
                            0,
                        )
                ),
            )
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun directRoomTerminalRetryIsBoundedAndCanResumeOnAnotherOwnedDevice() {
        val room = DirectRoomFixture()
        val f = room.f
        success(room.request())
        assertEquals(
            "collaboration_room_provisioning_changed",
            failed(room.request(operation = UUID.randomUUID(), expected = 0)),
        )
        val old = room.pointer()
        sql.update(
            "update background_jobs set status='FAILED',finished_at=now() where id=?",
            old.jobId,
        )
        assertTrue(success(room.status()).retryable)
        assertEquals(old.updatedAt.plusSeconds(60), success(room.status()).retryAt)
        assertEquals(
            "collaboration_provisioning_retry_too_soon",
            failed(room.request(operation = UUID.randomUUID(), expected = 0)),
        )
        val parent = f.parent()
        val device = success(f.register(parent)).id
        f.activate(device)
        f.clock.at = f.clock.at.plusSeconds(61)
        val op = UUID.randomUUID()
        val receipt =
            success(room.request(parent = parent, device = device, operation = op, expected = 0))
        assertEquals(1, receipt.version)
        assertEquals(device, room.pointer().deviceId)
        assertEquals(old.createdAt, room.pointer().createdAt)
        assertEquals(
            receipt.copy(replayed = true),
            success(room.request(parent = parent, device = device, operation = op, expected = 0)),
        )
        assertEquals(
            "collaboration_room_provisioning_changed",
            failed(
                room.request(
                    parent = parent,
                    device = device,
                    operation = UUID.randomUUID(),
                    expected = 0,
                )
            ),
        )
    }

    @Test
    fun directRoomLateExpiryRollsBackJobPointerReceiptAuditAndOutbox() {
        val room = DirectRoomFixture()
        val f = room.f
        val tables =
            listOf(
                "background_jobs",
                "collaboration_direct_room_provisioning",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
        val before = tables.associateWith(f::rows)
        val journal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    f.journal.record(actor, change).also {
                        f.clock.at = f.clock.at.plusSeconds(901)
                    }
            }
        assertEquals(
            "native_session_invalid",
            failed(room.request(command = f.requestRoom(journal = journal))),
        )
        assertEquals(before, tables.associateWith(f::rows))
        assertEquals("native_session_invalid", failed(room.status()))
    }

    @Test
    fun directRoomInterruptionRollsBackAndStatusCannotExposeLateAcquiredMetadata() {
        val room = DirectRoomFixture()
        val f = room.f
        val before = f.rows("background_jobs")
        val journal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
                    f.journal.record(actor, change)
                    throw InterruptedException("fixture room cancellation")
                }
            }
        try {
            assertThrows(InterruptedException::class.java) {
                room.request(command = f.requestRoom(journal = journal))
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(before, f.rows("background_jobs"))
        assertEquals(0, f.rows("collaboration_direct_room_provisioning"))
        success(room.request())
        val late =
            object : CollaborationDirectRoomRepository by f.directRooms {
                override fun provisioning(
                    companyId: UUID,
                    conversationId: UUID,
                ): Result<CollaborationDirectRoomProvisioning?> =
                    f.directRooms.provisioning(companyId, conversationId).also {
                        f.clock.at = f.clock.at.plusSeconds(901)
                    }
            }
        assertEquals(
            "native_session_invalid",
            failed(
                f.roomStatus(late).execute(f.actor, room.parent, 0, room.client, room.conversation)
            ),
        )
    }

    @Test
    fun directRoomWorkerRecoversLostCreateResponseWithoutDuplicateRoomOrLogout() {
        val room = DirectRoomFixture()
        success(room.request())
        val lease = room.f.claimRoom(room.conversation)
        val matrix = RecordingMatrix(room.f.clock)
        val provider = RecordingRooms(room)
        provider.loseCreateResponse = true
        val worker = room.f.advanceRoom(matrix, provider)
        assertEquals("collaboration_provider_unavailable", failed(worker.execute(lease)))
        assertEquals(0, room.f.rows("collaboration_direct_room_bindings"))
        provider.loseCreateResponse = false
        assertEquals(JobStep(1, true), success(worker.execute(lease)))
        assertEquals(1, provider.creates)
        assertEquals(CollaborationDirectRoomState.READY, success(room.status()).state)
        assertEquals(provider.id, success(room.status()).matrixRoomId)
        assertEquals("SUCCEEDED", room.f.job(lease).status.name)
        assertEquals(0, matrix.logouts.get())
        assertEquals("job_lease_lost", failed(worker.execute(lease)))
    }

    @Test
    fun directRoomWorkerRepairsOnlyMissingVerifiedStateAndRefusesForeignMarker() {
        for (foreign in listOf(false, true)) {
            val room = DirectRoomFixture()
            success(room.request())
            val lease = room.f.claimRoom(room.conversation)
            val matrix = RecordingMatrix(room.f.clock)
            val provider = RecordingRooms(room)
            provider.existing = true
            provider.value =
                provider.value.copy(
                    encryptionAlgorithm = null,
                    historyVisibility = null,
                    joinRule = null,
                    guestAccess = null,
                    power = null,
                    members = mapOf(provider.creator to "join"),
                    marker =
                        if (foreign)
                            CollaborationMatrixRoomMarker(
                                UUID.randomUUID().toString(),
                                room.conversation.toString(),
                                "DIRECT",
                            )
                        else provider.value.marker,
                )
            val result = room.f.advanceRoom(matrix, provider).execute(lease)
            if (foreign) {
                assertEquals("collaboration_room_mismatch", failed(result))
                assertEquals(0, room.f.rows("collaboration_direct_room_bindings"))
                assertFalse(provider.calls.any { it.startsWith("setting:") || it == "invite" })
            } else {
                success(result)
                assertEquals(5, provider.calls.count { it.startsWith("setting:") })
                assertEquals(1, provider.calls.count { it == "invite" })
                assertEquals(1, room.f.rows("collaboration_direct_room_bindings"))
            }
            assertEquals(0, provider.creates)
            assertEquals(0, matrix.logouts.get())
        }
    }

    @Test
    fun directRoomWorkerRechecksRevokedPeerAfterProviderResponseAndAbortOwnsOnlyJob() {
        val room = DirectRoomFixture()
        success(room.request())
        val lease = room.f.claimRoom(room.conversation)
        val matrix = RecordingMatrix(room.f.clock)
        val provider = RecordingRooms(room)
        provider.onCall = { name ->
            if (name == "observe")
                sql.update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                    room.f.actor.companyId,
                    room.peer,
                )
            Result.Success(Unit)
        }
        val result = room.f.advanceRoom(matrix, provider).execute(lease)
        assertEquals("collaboration_peer_unavailable", failed(result))
        assertEquals(0, room.f.rows("collaboration_direct_room_bindings"))
        success(room.f.abort().execute(lease, (result as Result.Failed).failure))
        assertEquals(JobStatus.FAILED, room.f.job(lease).status)
        assertEquals(CollaborationDirectRoomState.FAILED, success(room.status()).state)
        assertEquals(0, matrix.logouts.get())
    }

    @Test
    fun directRoomWorkerTreatsChangedNativeObservationAsRetryableAndWithholdsBinding() {
        val room = DirectRoomFixture()
        success(room.request())
        val lease = room.f.claimRoom(room.conversation)
        val matrix = RecordingMatrix(room.f.clock)
        val provider = RecordingRooms(room)
        provider.onCall = { name ->
            if (name == "create")
                sql.update("update native_sessions set version=version+1 where id=?", room.parent)
            Result.Success(Unit)
        }
        val worker = room.f.advanceRoom(matrix, provider)
        val result = worker.execute(lease)
        assertEquals("collaboration_admission_changed", failed(result))
        assertEquals(FailureKind.UNAVAILABLE, (result as Result.Failed).failure.kind)
        assertEquals(0, room.f.rows("collaboration_direct_room_bindings"))
        provider.onCall = { Result.Success(Unit) }
        success(worker.execute(lease))
        assertEquals(1, provider.creates)
        assertEquals(0, matrix.logouts.get())
    }

    @Test
    fun directRoomWorkerDeadlineAndProviderFailureCannotOverrideCurrentDenial() {
        for (revoke in listOf(false, true)) {
            val room = DirectRoomFixture()
            success(room.request())
            val lease = room.f.claimRoom(room.conversation)
            val matrix = RecordingMatrix(room.f.clock)
            val provider = RecordingRooms(room)
            provider.onCall = {
                if (revoke)
                    sql.update(
                        "update native_sessions set revoked_at=now() where id=?",
                        room.parent,
                    )
                else room.f.clock.at = room.f.clock.at.plusSeconds(61)
                Result.Failed(
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable")
                )
            }
            val result = room.f.advanceRoom(matrix, provider).execute(lease)
            assertEquals(
                if (revoke) "native_session_invalid" else "collaboration_admission_changed",
                failed(result),
            )
            assertEquals(0, room.f.rows("collaboration_direct_room_bindings"))
            assertEquals(0, matrix.logouts.get())
        }
    }

    @Test
    fun directRoomWorkerInterruptedPublicationRollsBackBindingJobAndJournalTogether() {
        val room = DirectRoomFixture()
        success(room.request())
        val lease = room.f.claimRoom(room.conversation)
        val matrix = RecordingMatrix(room.f.clock)
        val provider = RecordingRooms(room)
        val tables = listOf("audit_entries", "outbox_events", "collaboration_direct_room_bindings")
        val before = tables.associateWith(room.f::rows)
        val journal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
                    room.f.journal.record(actor, change)
                    throw InterruptedException("fixture room publication")
                }
            }
        try {
            assertThrows(InterruptedException::class.java) {
                room.f.advanceRoom(matrix, provider, journal).execute(lease)
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(before, tables.associateWith(room.f::rows))
        assertEquals(JobStatus.RUNNING, room.f.job(lease).status)
        assertEquals(0, room.f.job(lease).completedItems)
        success(room.f.advanceRoom(matrix, provider).execute(lease))
        assertEquals(1, provider.creates)
        assertEquals(0, matrix.logouts.get())
    }

    private class RecordingRooms(room: DirectRoomFixture) : CollaborationMatrixRoomRepository {
        val id = "!fictional-${UUID.randomUUID()}"
        val creator =
            "@${collaborationMatrixLocalpart(requireNotNull(room.f.actor.companyId),room.f.account)}:proof.invalid"
        val peer =
            "@${collaborationMatrixLocalpart(requireNotNull(room.f.actor.companyId),room.peer)}:proof.invalid"
        var value =
            CollaborationMatrixRoomObservation(
                creator,
                "12",
                false,
                CollaborationMatrixRoomMarker(
                    room.f.actor.companyId.toString(),
                    room.conversation.toString(),
                    "DIRECT",
                ),
                emptyList(),
                false,
                "m.megolm.v1.aes-sha2",
                "invite",
                "invited",
                "forbidden",
                collaborationDirectRoomPower(),
                mapOf(creator to "join", peer to "invite"),
            )
        var existing = false
        var creates = 0
        var loseCreateResponse = false
        val calls = mutableListOf<String>()
        var onCall: (String) -> Result<Unit> = { Result.Success(Unit) }

        private fun step(name: String): Result<Unit> {
            assertFalse(
                org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()
            )
            calls += name
            return onCall(name)
        }

        override fun resolve(
            request: CollaborationMatrixDirectRoom,
            token: String,
            deadline: Instant,
        ): Result<String?> = step("resolve").map { if (existing) id else null }

        override fun create(
            request: CollaborationMatrixDirectRoom,
            token: String,
            deadline: Instant,
        ): Result<CollaborationMatrixRoomCreation> {
            existing = true
            creates++
            return step("create").flatMap {
                if (loseCreateResponse)
                    Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable")
                    )
                else Result.Success(CollaborationMatrixRoomCreation.Created(id))
            }
        }

        override fun observe(
            roomId: String,
            token: String,
            deadline: Instant,
        ): Result<CollaborationMatrixRoomObservation> = step("observe").map { value }

        override fun putInitialSetting(
            roomId: String,
            setting: CollaborationMatrixRoomSetting,
            token: String,
            deadline: Instant,
        ): Result<Unit> =
            step("setting:$setting").map {
                value =
                    when (setting) {
                        CollaborationMatrixRoomSetting.POWER_LEVELS ->
                            value.copy(power = collaborationDirectRoomPower())
                        CollaborationMatrixRoomSetting.ENCRYPTION ->
                            value.copy(encryptionAlgorithm = "m.megolm.v1.aes-sha2")
                        CollaborationMatrixRoomSetting.JOIN_RULES -> value.copy(joinRule = "invite")
                        CollaborationMatrixRoomSetting.HISTORY_VISIBILITY ->
                            value.copy(historyVisibility = "invited")
                        CollaborationMatrixRoomSetting.GUEST_ACCESS ->
                            value.copy(guestAccess = "forbidden")
                    }
            }

        override fun invitePeer(
            roomId: String,
            request: CollaborationMatrixDirectRoom,
            token: String,
            deadline: Instant,
        ): Result<Unit> =
            step("invite").map { value = value.copy(members = value.members + (peer to "invite")) }
    }

    private class DirectRoomFixture {
        val f = Fixture()
        val client = ClientVersion(ClientPlatform.ANDROID, 1)
        val peer = f.peer("Fictional room peer")
        val parent = f.parent()
        val device = (f.register(parent) as Result.Success).value.id
        val conversation =
            (f.openDirect().execute(f.actor, parent, 0, client, UUID.randomUUID(), peer)
                    as Result.Success)
                .value
                .id
        val operation = UUID.randomUUID()

        init {
            f.activate(device)
        }

        fun request(
            operation: UUID = this.operation,
            parent: UUID = this.parent,
            device: UUID = this.device,
            version: Long = 1,
            expected: Long? = null,
            client: ClientVersion? = this.client,
            conversation: UUID = this.conversation,
            command: RequestNativeCollaborationDirectRoom = f.requestRoom(),
        ) =
            command.execute(
                f.actor,
                parent,
                0,
                client,
                operation,
                conversation,
                device,
                version,
                expected,
            )

        fun status() = f.roomStatus().execute(f.actor, parent, 0, client, conversation)

        fun pointer() =
            requireNotNull(
                (f.transactions.run(f.actor) {
                        f.directRooms.provisioning(requireNotNull(f.actor.companyId), conversation)
                    } as Result.Success)
                    .value
            )
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

    private class Fixture(val account: UUID = UUID.randomUUID()) {
        val clock = MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
        private val dsl = DSL.using(TransactionAwareDataSourceProxy(runtime), SQLDialect.POSTGRES)
        private val json = JsonMapper.builder().build()
        private val receipts = PostgresOperationReceiptDataSource(dsl)
        val deviceKeys =
            StoredCollaborationDeviceKeyRepository(PostgresCollaborationDeviceKeyDataSource(dsl))
        val recipients =
            StoredCollaborationRecipientRepository(PostgresCollaborationRecipientDataSource(dsl))
        val directRooms =
            StoredCollaborationDirectRoomRepository(PostgresCollaborationDirectRoomDataSource(dsl))
        val direct = StoredCollaborationDirectRepository(PostgresCollaborationDirectDataSource(dsl))
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
        val members = StoredMembershipRepository(PostgresMembershipDataSource(dsl))
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

        fun parent(account: UUID = this.account): UUID {
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

        fun peer(
            name: String,
            company: UUID = requireNotNull(actor.companyId),
            grant: Boolean = true,
            active: Boolean = true,
            memberActive: Boolean = true,
            id: UUID = UUID.randomUUID(),
        ): UUID {
            sql.update(
                "insert into accounts(id,email,display_name,active) values(?,?,?,?)",
                id,
                "$id@example.test",
                name,
                active,
            )
            sql.update(
                "insert into company_memberships(company_id,account_id,active) values(?,?,?)",
                company,
                id,
                memberActive,
            )
            if (grant)
                sql.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    company,
                    id,
                )
            return id
        }

        fun advanceRoom(
            matrix: CollaborationMatrixRepository,
            providerRooms: CollaborationMatrixRoomRepository,
            journal: ChangeJournalRepository = this.journal,
        ) =
            AdvanceNativeCollaborationDirectRoom(
                direct,
                directRooms,
                registry,
                credentials,
                matrix,
                providerRooms,
                jobs,
                sessions,
                policies,
                companies,
                members,
                identities,
                journal,
                transactions,
                IdentitySecurityPolicy(),
                CollaborationServicePolicy(true),
                CollaborationMatrixPolicy(true, "proof.invalid"),
                clock,
            )

        fun claimRoom(conversation: UUID): JobLease {
            val pointer =
                requireNotNull(
                    success(
                        transactions.run(actor) {
                            directRooms.provisioning(requireNotNull(actor.companyId), conversation)
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
            return JobLease(
                requireNotNull(
                    success(
                        transactions.run(actor) {
                            jobs.find(requireNotNull(actor.companyId), pointer.jobId)
                        }
                    )
                ),
                owner,
                token,
                deadline,
            )
        }

        fun requestRoom(
            journal: ChangeJournalRepository = this.journal,
            provider: CollaborationMatrixPolicy = CollaborationMatrixPolicy(true, "proof.invalid"),
        ) =
            RequestNativeCollaborationDirectRoom(
                direct,
                directRooms,
                registry,
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
                CollaborationServicePolicy(true),
                provider,
                clock,
            )

        fun roomStatus(rooms: CollaborationDirectRoomRepository = directRooms) =
            GetNativeCollaborationDirectRoomStatus(
                direct,
                rooms,
                jobs,
                sessions,
                policies,
                companies,
                members,
                identities,
                transactions,
                IdentitySecurityPolicy(),
                CollaborationServicePolicy(true),
                CollaborationMatrixPolicy(true, "proof.invalid"),
                clock,
            )

        fun openDirect(
            conversations: CollaborationDirectRepository = direct,
            journal: ChangeJournalRepository = this.journal,
            identities: IdentityRepository = this.identities,
            security: IdentitySecurityPolicy = IdentitySecurityPolicy(),
        ) =
            OpenNativeCollaborationDirect(
                conversations,
                sessions,
                policies,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                security,
                CollaborationServicePolicy(true),
                CollaborationMatrixPolicy(true, "proof.invalid"),
                clock,
            )

        fun readDirect(
            conversations: CollaborationDirectRepository = direct,
            security: IdentitySecurityPolicy = IdentitySecurityPolicy(),
        ) =
            ReadNativeCollaborationDirect(
                conversations,
                sessions,
                policies,
                companies,
                members,
                identities,
                transactions,
                security,
                CollaborationServicePolicy(true),
                CollaborationMatrixPolicy(true, "proof.invalid"),
                clock,
            )

        fun participants(
            members: MembershipRepository = this.members,
            security: IdentitySecurityPolicy = IdentitySecurityPolicy(),
            provider: CollaborationMatrixPolicy = CollaborationMatrixPolicy(true, "proof.invalid"),
        ) =
            ListNativeCollaborationParticipants(
                sessions,
                policies,
                companies,
                members,
                identities,
                transactions,
                security,
                CollaborationServicePolicy(true),
                provider,
                clock,
            )

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
                gateway,
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

        fun matrixIdentity(
            matrix: CollaborationMatrixRepository,
            journal: ChangeJournalRepository = this.journal,
            serverName: String = "proof.invalid",
            providerKeys: CollaborationMatrixKeyRepository =
                DisabledCollaborationMatrixKeyRepository(),
            keyStore: CollaborationDeviceKeyRepository = deviceKeys,
        ) =
            AccessNativeCollaborationMatrixDevice(
                gateway,
                registry,
                credentials,
                matrix,
                keyStore,
                providerKeys,
                recipients,
                direct,
                sessions,
                policies,
                companies,
                members,
                identities,
                journal,
                transactions,
                IdentitySecurityPolicy(),
                CollaborationServicePolicy(true),
                CollaborationMatrixPolicy(true, serverName),
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
