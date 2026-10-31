package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationRecipientDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationRecipientRepository
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationRecipientStorageTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
        lateinit var owner: JdbcTemplate
        private lateinit var upgraded: Fixture

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val source =
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            owner = JdbcTemplate(source)
            Flyway.configure().dataSource(source).target("112").load().migrate()
            owner.execute("create role recipient_fixture login password 'recipient-fixture-only'")
            owner.execute("grant usage on schema public to recipient_fixture")
            owner.execute(
                "grant select,insert,update,delete on all tables in schema public to recipient_fixture"
            )
            upgraded = Fixture()
            val before = snapshots()
            Flyway.configure().dataSource(source).target("113").load().migrate()
            assertEquals(before, snapshots())
            owner.execute("grant select on collaboration_recipient_devices to recipient_fixture")
        }

        fun snapshots() =
            listOf(
                    "collaboration_devices",
                    "collaboration_device_sessions",
                    "collaboration_device_key_publications",
                )
                .associateWith { table ->
                    owner.queryForList(
                        "select row_to_json(t)::text as value from $table t order by 1"
                    )
                }
    }

    @Test
    fun projectionIsReadOnlyAndDoesNotExpandPrivateRegistryScope() {
        val f = Fixture()
        val values = f.read()
        assertEquals(setOf(f.sender.device, f.peer.device), values.map { it.deviceId }.toSet())
        assertTrue(values.all { it.companyId == f.company && it.receiveUntil > Instant.now() })
        val columns =
            owner.queryForList(
                "select column_name from information_schema.columns where table_name='collaboration_recipient_devices' order by ordinal_position",
                String::class.java,
            )
        assertEquals(
            listOf("company_id", "account_id", "device_id", "fingerprint", "receive_until"),
            columns,
        )
        f.run {
            assertEquals(
                0,
                f.runtime.queryForObject(
                    "select count(*) from collaboration_devices where account_id=?",
                    Int::class.java,
                    f.peer.account,
                ),
            )
            assertEquals(
                0,
                f.runtime.queryForObject(
                    "select count(*) from collaboration_device_sessions where account_id=?",
                    Int::class.java,
                    f.peer.account,
                ),
            )
            assertEquals(
                0,
                f.runtime.queryForObject(
                    "select count(*) from collaboration_device_key_publications where account_id=?",
                    Int::class.java,
                    f.peer.account,
                ),
            )
            Result.Success(Unit)
        }
        assertThrows(DataAccessException::class.java) {
            f.runtime.execute("set role hris_collaboration_recipient_reader")
        }
        assertInstanceOf(
            Result.Failed::class.java,
            f.run {
                f.runtime.update(
                    "delete from collaboration_recipient_devices where device_id=?",
                    f.peer.device,
                )
                Result.Success(Unit)
            },
        )
        assertEquals(
            false,
            owner.queryForObject(
                "select rolcanlogin or rolsuper or rolinherit or rolcreaterole or rolcreatedb or rolbypassrls from pg_roles where rolname='hris_collaboration_recipient_reader'",
                Boolean::class.java,
            ),
        )
        for ((table, column) in
            listOf(
                "native_sessions" to "access_hash",
                "native_sessions" to "refresh_hash",
                "collaboration_devices" to "label",
                "accounts" to "password_hash",
            )) {
            assertEquals(
                false,
                owner.queryForObject(
                    "select has_column_privilege('hris_collaboration_recipient_reader',?,?,'SELECT')",
                    Boolean::class.java,
                    table,
                    column,
                ),
            )
        }
        assertEquals(
            listOf("security_barrier=true"),
            owner.queryForList(
                "select unnest(reloptions) from pg_class where oid='collaboration_recipient_devices'::regclass",
                String::class.java,
            ),
        )
    }

    @Test
    fun companyAndConversationScopeNeverBecomeDirectoryWideKeyDiscovery() {
        val f = Fixture()
        val other = Fixture()
        val stranger = f.participant()
        assertEquals(emptyList<Any>(), f.read(accounts = setOf(stranger.account)))
        assertEquals(
            emptyList<Any>(),
            f.read(
                actor = f.actor.copy(accountId = stranger.account),
                accounts = setOf(f.peer.account),
            ),
        )
        assertEquals(emptyList<Any>(), f.read(actor = f.actor.copy(companyId = other.company)))
        assertEquals(emptyList<Any>(), f.read(actor = f.actor.copy(companyId = null)))
        assertEquals(
            emptyList<Any>(),
            f.read(company = other.company, accounts = setOf(other.peer.account)),
        )
        assertEquals(emptyList<Any>(), f.read(actor = f.actor.copy(accountId = UUID.randomUUID())))
        assertEquals(
            setOf(f.sender.device, f.peer.device),
            f.read(actor = f.actor.copy(accountId = f.peer.account)).map { it.deviceId }.toSet(),
        )
    }

    @Test
    fun accessExpiryAndRotationRetainOfflineRecipientButCurrentRevocationsRemoveIt() {
        val changes: List<(Fixture) -> Unit> =
            listOf(
                { f ->
                    owner.update(
                        "update native_sessions set expires_at=now()-interval '1 second' where id=?",
                        f.peer.parent,
                    )
                },
                { f ->
                    owner.update(
                        "update native_sessions set revoked_at=now() where id=?",
                        f.peer.parent,
                    )
                },
                { f ->
                    owner.update(
                        "update accounts set security_version=security_version+1,version=version+1 where id=?",
                        f.peer.account,
                    )
                },
                { f ->
                    owner.update(
                        "update accounts set active=false,version=version+1 where id=?",
                        f.peer.account,
                    )
                },
                { f ->
                    owner.update(
                        "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                        f.company,
                        f.peer.account,
                    )
                },
                { f ->
                    owner.update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                        f.company,
                        f.peer.account,
                    )
                },
                { f ->
                    owner.update(
                        "update collaboration_devices set state='REVOKED',revoked_at=now(),updated_at=now(),version=version+1 where company_id=? and id=?",
                        f.company,
                        f.peer.device,
                    )
                },
            )
        for (change in changes) {
            val f = Fixture()
            owner.update(
                "update native_sessions set access_expires_at=now()-interval '1 hour',authenticated_at=now()-interval '1 day',mfa_verified_at=null,version=version+1 where id=?",
                f.peer.parent,
            )
            assertEquals(
                listOf(f.peer.device),
                f.read(accounts = setOf(f.peer.account)).map { it.deviceId },
            )
            change(f)
            assertEquals(emptyList<Any>(), f.read(accounts = setOf(f.peer.account)))
            assertEquals(
                1,
                owner.queryForObject(
                    "select count(*) from collaboration_device_key_publications where device_id=?",
                    Int::class.java,
                    f.peer.device,
                ),
            )
        }
        val f = Fixture()
        assertEquals(emptyList<Any>(), f.read(at = Instant.now().plusSeconds(172_800)))
        owner.update("update companies set active=false where id=?", f.company)
        assertEquals(emptyList<Any>(), f.read())
    }

    @Test
    fun unconfirmedKeysAndDeletedParentsStayRetainedWithoutRecipientEligibility() {
        val f = Fixture()
        val pending = f.participant(published = false)
        f.pair(f.sender.account, pending.account)
        assertEquals(emptyList<Any>(), f.read(accounts = setOf(pending.account)))
        owner.update("delete from native_sessions where id=?", f.peer.parent)
        assertEquals(emptyList<Any>(), f.read(accounts = setOf(f.peer.account)))
        assertEquals(
            1,
            owner.queryForObject(
                "select count(*) from collaboration_devices where id=?",
                Int::class.java,
                f.peer.device,
            ),
        )
        assertEquals(
            1,
            owner.queryForObject(
                "select count(*) from collaboration_device_key_publications where device_id=?",
                Int::class.java,
                f.peer.device,
            ),
        )
        assertEquals(
            0,
            owner.queryForObject(
                "select count(*) from collaboration_device_sessions where device_id=?",
                Int::class.java,
                f.peer.device,
            ),
        )
        val failed = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_late_denial"))
        assertEquals(
            failed,
            f.run {
                f.runtime.update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                    f.company,
                    f.sender.account,
                )
                assertEquals(
                    Result.Success(emptyList<Any>()),
                    f.repository.devices(f.company, setOf(f.sender.account), Instant.now()),
                )
                failed
            },
        )
        assertEquals(
            listOf(f.sender.device),
            f.read(accounts = setOf(f.sender.account)).map { it.deviceId },
        )
    }

    @Test
    fun liveCallerPermissionAndAccountStatusAreRequiredForEveryProjectionRead() {
        val changes: List<(Fixture) -> Unit> =
            listOf(
                { f ->
                    owner.update(
                        "update accounts set active=false,version=version+1 where id=?",
                        f.sender.account,
                    )
                },
                { f ->
                    owner.update(
                        "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                        f.company,
                        f.sender.account,
                    )
                },
                { f ->
                    owner.update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                        f.company,
                        f.sender.account,
                    )
                },
            )
        for (change in changes) {
            val f = Fixture()
            assertEquals(2, f.read().size)
            change(f)
            assertEquals(emptyList<Any>(), f.read())
        }
        val f = Fixture()
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.repository.devices(f.company, emptySet(), Instant.now()) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.run {
                f.repository.devices(
                    f.company,
                    (1..101).map { UUID.randomUUID() }.toSet(),
                    Instant.now(),
                )
            },
        )
    }

    @Test
    fun multipleParentsYieldOneDeviceAndOnlyCurrentParentsExtendItsDeadline() {
        val f = Fixture()
        val extra = UUID.randomUUID()
        val hash = UUID.randomUUID().toString().replace("-", "").repeat(2)
        owner.update(
            "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version) values(?,?,?,now(),now()+interval '2 days',0)",
            extra,
            f.peer.account,
            hash,
        )
        owner.update(
            "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,now())",
            f.company,
            f.peer.device,
            f.peer.account,
            extra,
        )
        val longest = f.read(accounts = setOf(f.peer.account)).single()
        assertTrue(longest.receiveUntil > Instant.now().plusSeconds(86_400))
        owner.update("update native_sessions set revoked_at=now() where id=?", extra)
        val remaining = f.read(accounts = setOf(f.peer.account)).single()
        assertEquals(longest.deviceId, remaining.deviceId)
        assertEquals(longest.fingerprint, remaining.fingerprint)
        assertTrue(remaining.receiveUntil < longest.receiveUntil)
        owner.update(
            "update native_sessions set expires_at=now()-interval '1 second' where id=?",
            f.peer.parent,
        )
        assertEquals(emptyList<Any>(), f.read(accounts = setOf(f.peer.account)))
    }

    @Test
    fun upgradeAndActualDumpRestorePreserveRestrictedViewOwnerAndCurrentEligibility() {
        assertEquals(2, upgraded.read().size)
        val f = Fixture()
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
            "/tmp/recipient.dump",
        )
        command("createdb", "-U", postgres.username, "recipient_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "recipient_restored",
            "--exit-on-error",
            "/tmp/recipient.dump",
        )
        val url =
            postgres.jdbcUrl.substringBefore('?').substringBeforeLast('/') + "/recipient_restored"
        val source = DriverManagerDataSource(url, "recipient_fixture", "recipient-fixture-only")
        val runtime = JdbcTemplate(source)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(source), runtime)
        val repository =
            StoredCollaborationRecipientRepository(
                PostgresCollaborationRecipientDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
                )
            )
        assertEquals(
            Result.Success(f.read()),
            tx.run(f.actor) {
                repository.devices(
                    f.company,
                    setOf(f.sender.account, f.peer.account),
                    Instant.now(),
                )
            },
        )
        assertEquals(
            "hris_collaboration_recipient_reader",
            runtime.queryForObject(
                "select pg_get_userbyid(relowner) from pg_class where oid='collaboration_recipient_devices'::regclass",
                String::class.java,
            ),
        )
        val restoredOwner =
            JdbcTemplate(DriverManagerDataSource(url, postgres.username, postgres.password))
        restoredOwner.update(
            "update native_sessions set revoked_at=now() where id=?",
            f.peer.parent,
        )
        assertEquals(
            Result.Success(emptyList<Any>()),
            tx.run(f.actor) { repository.devices(f.company, setOf(f.peer.account), Instant.now()) },
        )
        tx.run(f.actor) {
            assertEquals(
                0,
                runtime.queryForObject(
                    "select count(*) from collaboration_devices where account_id=?",
                    Int::class.java,
                    f.peer.account,
                ),
            )
            Result.Success(Unit)
        }
        assertThrows(DataAccessException::class.java) {
            runtime.execute("set role hris_collaboration_recipient_reader")
        }
    }

    private data class Participant(val account: UUID, val device: UUID, val parent: UUID)

    private class Fixture {
        val company = UUID.randomUUID()
        private val source =
            DriverManagerDataSource(postgres.jdbcUrl, "recipient_fixture", "recipient-fixture-only")
        val runtime = JdbcTemplate(source)
        private val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(source), runtime)
        val repository =
            StoredCollaborationRecipientRepository(
                PostgresCollaborationRecipientDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
                )
            )
        val sender: Participant
        val peer: Participant
        val actor: Actor

        init {
            owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional recipient company','UTC')",
                company,
                company.toString().replace("-", ""),
            )
            sender = participant()
            peer = participant()
            actor = Actor(sender.account, company, emptySet(), Instant.now(), UUID.randomUUID())
            pair(sender.account, peer.account)
        }

        fun participant(published: Boolean = true): Participant {
            val value = Participant(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional recipient')",
                value.account,
                "${value.account}@example.test",
            )
            owner.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                value.account,
            )
            owner.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                company,
                value.account,
            )
            val hash = UUID.randomUUID().toString().replace("-", "").repeat(2)
            owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,access_hash,access_expires_at,credential_version) values(?,?,?,now()-interval '1 hour',now()+interval '1 day',?,now()-interval '1 minute',0)",
                value.parent,
                value.account,
                hash,
                hash,
            )
            owner.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Private fixture label','PROVISIONING',0,now()-interval '1 minute',now()-interval '1 minute')",
                company,
                value.device,
                value.account,
            )
            owner.update(
                "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
                company,
                value.device,
            )
            owner.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,now())",
                company,
                value.device,
                value.account,
                value.parent,
            )
            owner.update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,repeat('a',64),0,now())",
                company,
                value.device,
                value.account,
            )
            if (published)
                owner.update(
                    "update collaboration_device_key_publications set version=1,published_at=now() where company_id=? and device_id=?",
                    company,
                    value.device,
                )
            return value
        }

        fun pair(a: UUID, b: UUID) {
            val sorted = listOf(a, b).sortedBy { it.toString() }
            owner.update(
                "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,now())",
                company,
                UUID.randomUUID(),
                sorted[0],
                sorted[1],
                a,
            )
        }

        fun read(
            actor: Actor = this.actor,
            company: UUID = this.company,
            accounts: Set<UUID> = setOf(sender.account, peer.account),
            at: Instant = Instant.now(),
        ) = (run(actor) { repository.devices(company, accounts, at) } as Result.Success).value

        fun <T> run(actor: Actor = this.actor, work: () -> Result<T>): Result<T> =
            transactions.run(actor, work)
    }
}
