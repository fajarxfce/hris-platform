package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceIdentityRepository
import dev.fajar.hris.collaboration.domain.usecases.SelectNativeCollaborationSpaceIdentity
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.core.database.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.*
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
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
class NativeCollaborationSpaceIdentitySelectionTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    private lateinit var owner: JdbcTemplate
    private lateinit var runtime: DriverManagerDataSource
    private lateinit var space: UUID
    private val now = Instant.parse("2026-11-03T20:00:00Z")
    private val server = "matrix.example.test"
    private val client = ClientVersion(ClientPlatform.ANDROID, 28)

    @BeforeAll
    fun database() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).load().migrate()
        owner = JdbcTemplate(source)
        owner.execute(
            "create role space_identity_command_fixture login password 'isolated-fixture-only'"
        )
        owner.execute("grant usage on schema public to space_identity_command_fixture")
        owner.execute(
            "grant select on all tables in schema public to space_identity_command_fixture"
        )
        owner.execute("grant update on accounts,native_sessions to space_identity_command_fixture")
        owner.execute(
            "grant insert on collaboration_space_identities,operation_receipts,audit_entries,platform_audit_metadata,outbox_events to space_identity_command_fixture"
        )
        runtime =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "space_identity_command_fixture",
                "isolated-fixture-only",
            )
        space =
            requireNotNull(
                owner.queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
    }

    @Test
    fun firstSelectionAndLostResponsesPreserveBothNewAndRetiredLegacyUsers() {
        for (legacy in listOf(false, true)) {
            val f = Fixture()
            val oldUser = if (legacy) f.retiredLegacy() else null
            val operation = UUID.randomUUID()
            val receipt = success(f.execute(operation))
            assertEquals(MutationReceipt(f.account, 0), receipt)
            assertEquals(receipt.copy(replayed = true), success(f.execute(operation)))
            assertEquals(receipt, success(f.execute(UUID.randomUUID())))
            f.counts(1, 2, 1, 1)
            val selected = f.selected()
            assertEquals(
                if (legacy) CollaborationSpaceIdentityOrigin.LEGACY
                else CollaborationSpaceIdentityOrigin.SPACE,
                selected.origin,
            )
            assertEquals(
                oldUser ?: "@hrisg_${compact(space)}_${compact(f.account)}:$server",
                selected.matrixUserId,
            )
            val details =
                owner.queryForObject(
                    "select details::text from audit_entries where actor_id=?",
                    String::class.java,
                    f.account,
                )!!
            assertEquals(
                setOf("spaceId", "origin", "version"),
                ObjectMapper().readTree(details).properties().map { it.key }.toSet(),
            )
            assertEquals(
                1,
                owner.queryForObject(
                    "select count(*) from audit_entries a join outbox_events o on o.id=a.id where a.actor_id=? and a.details=o.payload and a.company_id is null and o.company_id is null",
                    Int::class.java,
                    f.account,
                ),
            )
            failure(
                f.execute(operation, requestedServer = "changed.example.test"),
                "operation_payload_mismatch",
            )
            failure(
                f.execute(UUID.randomUUID(), requestedServer = "changed.example.test"),
                "collaboration_identity_migration_required",
            )
            assertEquals(selected, f.selected())
            f.counts(1, 2, 1, 1)
        }
    }

    @Test
    fun twoConcurrentNativeParentsSerializeFirstSelectionAndDuplicateReceipts() {
        for (sameOperation in listOf(false, true)) {
            val f = Fixture()
            f.retiredLegacy()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val selections = AtomicInteger()
            val hooked =
                object : CollaborationSpaceIdentityRepository by f.selections {
                    override fun selection(
                        spaceId: UUID,
                        accountId: UUID,
                        serverName: String,
                    ): Result<CollaborationSpaceIdentityInput?> {
                        if (selections.incrementAndGet() == 1) {
                            entered.countDown()
                            check(release.await(10, TimeUnit.SECONDS))
                        }
                        return f.selections.selection(spaceId, accountId, serverName)
                    }
                }
            val operation = UUID.randomUUID()
            val secondParent = f.parent()
            val executor = Executors.newFixedThreadPool(2)
            try {
                val first =
                    executor.submit<MutationReceipt> {
                        success(f.execute(operation, selections = hooked))
                    }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                val second =
                    executor.submit<MutationReceipt> {
                        success(
                            f.execute(
                                if (sameOperation) operation else UUID.randomUUID(),
                                session = secondParent,
                                selections = hooked,
                            )
                        )
                    }
                // Observe the PostgreSQL lock wait, not just thread scheduling or a fixed sleep.
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
                while (
                    owner.queryForObject(
                        "select count(*) from pg_stat_activity where usename='space_identity_command_fixture' and wait_event_type='Lock'",
                        Int::class.java,
                    ) == 0 && System.nanoTime() < deadline
                ) Thread.sleep(20)
                assertTrue(
                    owner.queryForObject(
                        "select count(*) from pg_stat_activity where usename='space_identity_command_fixture' and wait_event_type='Lock'",
                        Int::class.java,
                    )!! > 0
                )
                assertFalse(second.isDone)
                assertEquals(1, selections.get())
                release.countDown()
                val a = first.get(10, TimeUnit.SECONDS)
                val b = second.get(10, TimeUnit.SECONDS)
                assertEquals(a.copy(replayed = sameOperation), b)
                assertEquals(2, selections.get())
                f.counts(1, if (sameOperation) 1 else 2, 1, 1)
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun everyWriteStageRollsBackFailuresAndCancellationBeforeAnIdenticalRetry() {
        for (stage in listOf("selection", "receipt", "journal")) for (cancel in
            listOf(false, true)) {
            val f = Fixture()
            val operation = UUID.randomUUID()
            fun injected(): Result<Unit> {
                if (cancel) throw CancellationException("fixture cancellation")
                return Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_write_failed"))
            }
            val selection =
                object : CollaborationSpaceIdentityRepository by f.selections {
                    override fun create(identity: CollaborationSpaceIdentity): Result<Unit> =
                        f.selections.create(identity).flatMap {
                            if (stage == "selection") injected() else Result.Success(Unit)
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
            val journal = hookJournal { actor, change ->
                f.journal.record(actor, change).flatMap {
                    if (stage == "journal") injected() else Result.Success(Unit)
                }
            }
            if (cancel)
                assertThrows(CancellationException::class.java) {
                    f.execute(
                        operation,
                        selections = selection,
                        operations = operations,
                        journal = journal,
                    )
                }
            else
                failure(
                    f.execute(
                        operation,
                        selections = selection,
                        operations = operations,
                        journal = journal,
                    ),
                    "fixture_write_failed",
                )
            f.counts(0, 0, 0, 0)
            assertFalse(success(f.execute(operation)).replayed)
            f.counts(1, 1, 1, 1)
        }
    }

    @Test
    fun lateCommittedOffboardingAndNewMfaRequirementRollBackAllSelectionWrites() {
        for (privileged in listOf(false, true)) {
            val f = Fixture()
            val operation = UUID.randomUUID()
            val journal = hookJournal { actor, change ->
                f.journal.record(actor, change).map {
                    // A separate connection commits the real access change after all four writes.
                    if (privileged)
                        owner.update(
                            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'identity.manage')",
                            f.company,
                            f.account,
                        )
                    else
                        owner.update(
                            "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                            f.company,
                            f.account,
                        )
                    Unit
                }
            }
            failure(
                f.execute(operation, journal = journal),
                if (privileged) "mfa_setup_required" else "collaboration_access_denied",
            )
            f.counts(0, 0, 0, 0)
            if (privileged)
                owner.update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission='identity.manage'",
                    f.company,
                    f.account,
                )
            else
                owner.update(
                    "update company_memberships set active=true,version=version+1 where company_id=? and account_id=?",
                    f.company,
                    f.account,
                )
            assertFalse(success(f.execute(operation)).replayed)
            f.counts(1, 1, 1, 1)
        }
    }

    @Test
    fun lateExpiryAndThreadInterruptionRollbackAndDoNotBecomeSuccessfulReceipts() {
        for (interrupt in listOf(false, true)) {
            val f = Fixture()
            val operation = UUID.randomUUID()
            val journal = hookJournal { actor, change ->
                f.journal.record(actor, change).map {
                    if (interrupt) Thread.currentThread().interrupt()
                    else f.clock.at = now.plusSeconds(900)
                    Unit
                }
            }
            if (interrupt) {
                try {
                    assertThrows(InterruptedException::class.java) {
                        f.execute(operation, journal = journal)
                    }
                    assertTrue(Thread.currentThread().isInterrupted)
                } finally {
                    Thread.interrupted()
                }
            } else failure(f.execute(operation, journal = journal), "native_session_invalid")
            f.counts(0, 0, 0, 0)
            f.clock.at = now
            assertFalse(success(f.execute(operation)).replayed)
            f.counts(1, 1, 1, 1)
        }
    }

    @Test
    fun replayStillRequiresCurrentAccountNativeParentClientAndGroupAccess() {
        val f = Fixture()
        val operation = UUID.randomUUID()
        success(f.execute(operation))
        val selected = f.selected()
        failure(
            f.execute(operation, request = f.actor.copy(companyId = f.company)),
            "platform_scope_required",
        )
        failure(f.execute(operation, build = client.copy(build = 27)), "client_update_required")
        failure(f.execute(operation, version = 1), "native_session_invalid")
        failure(f.execute(operation, session = UUID.randomUUID()), "native_session_invalid")
        val foreign = Fixture()
        failure(f.execute(operation, session = foreign.session), "native_session_invalid")
        failure(
            f.execute(operation, request = f.actor.copy(authenticatedAt = now)),
            "native_session_invalid",
        )
        owner.update(
            "update native_sessions set revoked_at=?::timestamptz,version=version+1 where id=?",
            now.toString(),
            f.session,
        )
        failure(f.execute(operation), "native_session_invalid")
        val next = f.parent()
        owner.update(
            "update accounts set security_version=security_version+1 where id=?",
            f.account,
        )
        failure(f.execute(operation, session = next), "session_revoked")
        assertEquals(selected, f.selected())
        f.counts(1, 1, 1, 1)
    }

    private inner class Fixture {
        val account = UUID.randomUUID()
        val company = UUID.randomUUID()
        val actor =
            Actor(
                account,
                null,
                emptySet(),
                now.minusSeconds(120),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val clock = MutableClock(now)
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
        val session: UUID

        init {
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
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                id,
                account,
                compact(id).repeat(2),
                actor.authenticatedAt.toString(),
                now.plusSeconds(3600).toString(),
                now.plusSeconds(900).toString(),
                now.minusSeconds(60).toString(),
                now.minusSeconds(30).toString(),
            )
            return id
        }

        fun retiredLegacy(): String {
            val device = UUID.randomUUID()
            val user = "@hris_${compact(company)}_${compact(account)}:$server"
            val at = now.minusSeconds(86400).toString()
            owner.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                company,
                device,
                account,
                at,
                at,
            )
            owner.update(
                "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-not-a-token',?::timestamptz,?::timestamptz,?::timestamptz+interval '5 minutes')",
                company,
                device,
                account,
                user,
                "HRIS_${compact(device)}",
                at,
                at,
                at,
            )
            owner.update(
                "update collaboration_devices set state='REVOKED',version=1,revoked_at=created_at+interval '1 minute',updated_at=created_at+interval '1 minute' where company_id=? and id=?",
                company,
                device,
            )
            owner.update(
                "update collaboration_provider_sessions set generation=1,token_encrypted=null,cleared_at=created_at+interval '1 minute',updated_at=created_at+interval '1 minute' where company_id=? and device_id=?",
                company,
                device,
            )
            return user
        }

        fun execute(
            operation: UUID,
            request: Actor = actor,
            session: UUID = this.session,
            version: Long = 0,
            build: ClientVersion? = client,
            requestedServer: String = server,
            selections: CollaborationSpaceIdentityRepository = this.selections,
            operations: OperationRepository = this.operations,
            journal: ChangeJournalRepository = this.journal,
        ): Result<MutationReceipt> =
            SelectNativeCollaborationSpaceIdentity(
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
                .execute(request, session, version, space, build, operation)

        fun selected(): CollaborationSpaceIdentity =
            requireNotNull(
                success(transactions.run(actor) { selections.selection(space, account, server) })
                    ?.selected
            )

        fun counts(selection: Int, receipts: Int, audits: Int, outbox: Int) {
            for ((table, column, count) in
                listOf(
                    Triple("collaboration_space_identities", "account_id", selection),
                    Triple("operation_receipts", "actor_id", receipts),
                    Triple("audit_entries", "actor_id", audits),
                    Triple("platform_audit_metadata", "actor_id", audits),
                    Triple("outbox_events", "actor_id", outbox),
                )) assertEquals(
                count,
                owner.queryForObject(
                    "select count(*) from $table where $column=?",
                    Int::class.java,
                    account,
                ),
                table,
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
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    private fun failure(result: Result<*>, code: String) {
        assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(code, (result as Result.Failed).failure.code)
    }
}
