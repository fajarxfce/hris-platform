package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.data.datasources.PostgresCompanyClientPolicyDataSource
import dev.fajar.hris.administration.data.repositories.StoredCompanyClientPolicyRepository
import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationRegistryDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationRegistryRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.core.database.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.organization.data.datasources.PostgresCompanyDataSource
import dev.fajar.hris.organization.data.repositories.StoredCompanyRepository
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
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
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )) assertEquals(0, f.rows(table), table)
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
        ) =
            RegisterNativeCollaborationDevice(
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
                clock,
            )

        fun register(
            parent: UUID,
            operation: UUID = UUID.randomUUID(),
            version: Long = 0,
            label: String = "Fictional emulator",
            actor: Actor = this.actor,
            client: ClientVersion = ClientVersion(ClientPlatform.ANDROID, 1),
            command: RegisterNativeCollaborationDevice = command(),
        ) =
            command.execute(
                actor,
                parent,
                version,
                operation,
                CollaborationPlatform.ANDROID,
                label,
                client,
            )

        fun acquire(parent: UUID, policies: CompanyClientPolicyRepository = this.policies) =
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
                )
                .execute(actor, parent, 0, ClientVersion(ClientPlatform.ANDROID, 1))

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
