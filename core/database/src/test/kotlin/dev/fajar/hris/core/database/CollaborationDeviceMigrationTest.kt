package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationDeviceMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun registryPreservesPermissionsScopesOwnershipAndRetainsIdentityAfterParentCleanup() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("103").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val otherCompany = UUID.randomUUID()
        val account = UUID.randomUUID()
        val otherAccount = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val otherParent = UUID.randomUUID()
        val device = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'CHAT','Chat fixture','UTC'),(?,'OTHER','Other fixture','UTC')",
            company,
            otherCompany,
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'chat-fixture@example.test','Chat fixture'),(?,'other-chat@example.test','Other fixture')",
            account,
            otherAccount,
        )
        sql.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            company,
            account,
        )
        sql.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'company.read')",
            company,
            account,
        )
        val original = sql.queryForList("select * from membership_permissions")
        for ((id, owner, hash) in
            listOf(Triple(parent, account, "a"), Triple(otherParent, otherAccount, "b"))) {
            sql.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,access_hash,access_expires_at,credential_version) values(?,?,repeat(?,64),now(),now()+interval '1 day',repeat(?,64),now()+interval '15 minutes',0)",
                id,
                owner,
                hash,
                hash,
            )
        }
        Flyway.configure().dataSource(source).target("104").load().migrate()
        assertEquals(original, sql.queryForList("select * from membership_permissions"))
        sql.execute("create role collaboration_fixture login password 'isolated-fixture-only'")
        sql.execute("grant usage on schema public to collaboration_fixture")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to collaboration_fixture"
        )
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "collaboration_fixture",
                "isolated-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val actor = Actor(account, company, emptySet(), Instant.now(), UUID.randomUUID())
        fun <T> run(current: Actor = actor, body: () -> T) =
            transactions.run(current) { safeDatabaseCall(body) }
        fun create(
            state: String = "PROVISIONING",
            owner: UUID = account,
            selectedCompany: UUID = company,
        ) =
            runtime.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional emulator',?,0,now(),now())",
                selectedCompany,
                device,
                owner,
                state,
            )
        assertInstanceOf(Result.Failed::class.java, run { create(state = "ACTIVE") })
        assertInstanceOf(Result.Failed::class.java, run { create(owner = otherAccount) })
        assertInstanceOf(Result.Failed::class.java, run { create(selectedCompany = otherCompany) })
        assertEquals(Result.Success(1), run { create() })
        fun bind(selectedParent: UUID = parent, selectedCompany: UUID = company) =
            runtime.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,now())",
                selectedCompany,
                device,
                account,
                selectedParent,
            )
        assertInstanceOf(Result.Failed::class.java, run { bind(selectedParent = otherParent) })
        assertInstanceOf(
            Result.Failed::class.java,
            run(actor.copy(companyId = otherCompany)) { bind(selectedCompany = otherCompany) },
        )
        assertEquals(Result.Success(1), run { bind() })
        assertInstanceOf(Result.Failed::class.java, run { bind() })

        fun count(table: String) =
            runtime.queryForObject("select count(*) from $table", Int::class.java)
        assertEquals(
            Result.Success(0),
            run(actor.copy(accountId = otherAccount)) { count("collaboration_devices") },
        )
        assertEquals(
            Result.Success(0),
            run(actor.copy(accountId = otherAccount)) { count("collaboration_device_sessions") },
        )
        // The owning account may count its devices across companies. Company
        // selection still cannot authorize a write to a different partition.
        assertEquals(
            Result.Success(1),
            run(actor.copy(companyId = otherCompany)) { count("collaboration_devices") },
        )
        assertEquals(
            Result.Success(0),
            run(actor.copy(companyId = otherCompany)) {
                runtime.update(
                    "update collaboration_devices set label='Changed',version=version+1 where id=?",
                    device,
                )
            },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            run { runtime.update("update collaboration_devices set version=2 where id=?", device) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            run {
                runtime.update(
                    "update collaboration_devices set platform='IOS',version=1 where id=?",
                    device,
                )
            },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            run {
                runtime.update(
                    "update collaboration_devices set updated_at='infinity',version=1 where id=?",
                    device,
                )
            },
        )
        assertEquals(
            Result.Success(1),
            run {
                runtime.update(
                    "update collaboration_devices set state='ACTIVE',version=1,updated_at=now() where id=?",
                    device,
                )
            },
        )
        assertEquals(
            Result.Success(1),
            run {
                runtime.update(
                    "update collaboration_devices set state='REVOKED',version=2,revoked_at=now(),updated_at=now() where id=?",
                    device,
                )
            },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            run {
                runtime.update(
                    "update collaboration_devices set state='ACTIVE',version=3,revoked_at=null,updated_at=now() where id=?",
                    device,
                )
            },
        )
        assertEquals(
            Result.Success(0),
            run { runtime.update("delete from collaboration_devices where id=?", device) },
        )
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            sql.update("delete from collaboration_devices where id=?", device)
        }
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            sql.update(
                "update collaboration_device_sessions set bound_at=now() where device_id=?",
                device,
            )
        }
        // Exercise the actual PostgreSQL backup format before parent cleanup;
        // the restored registry must retain its constraints and account RLS.
        fun command(vararg arguments: String) {
            val result = postgres.execInContainer(*arguments)
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
            "/tmp/collaboration.dump",
        )
        command("createdb", "-U", postgres.username, "collaboration_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "collaboration_restored",
            "--exit-on-error",
            "--no-owner",
            "--clean",
            "--if-exists",
            "/tmp/collaboration.dump",
        )
        val restoredUrl =
            postgres.jdbcUrl.substringBefore('?').substringBeforeLast('/') +
                "/collaboration_restored"
        val restored =
            JdbcTemplate(DriverManagerDataSource(restoredUrl, postgres.username, postgres.password))
        assertEquals(
            sql.queryForList("select * from collaboration_devices"),
            restored.queryForList("select * from collaboration_devices"),
        )
        assertEquals(
            sql.queryForList("select * from collaboration_device_sessions"),
            restored.queryForList("select * from collaboration_device_sessions"),
        )
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            restored.update(
                "update collaboration_devices set state='ACTIVE',version=3,revoked_at=null,updated_at=now() where id=?",
                device,
            )
        }
        val restoredRuntimeSource =
            DriverManagerDataSource(restoredUrl, "collaboration_fixture", "isolated-fixture-only")
        val restoredRuntime = JdbcTemplate(restoredRuntimeSource)
        val restoredTransactions =
            PostgresTransactionRunner(
                JdbcTransactionManager(restoredRuntimeSource),
                restoredRuntime,
            )
        assertEquals(
            Result.Success(0),
            restoredTransactions.run(actor.copy(accountId = otherAccount)) {
                safeDatabaseCall {
                    restoredRuntime.queryForObject(
                        "select count(*) from collaboration_devices",
                        Int::class.java,
                    )
                }
            },
        )
        assertEquals(1, sql.update("delete from native_sessions where id=?", parent))
        assertEquals(Result.Success(0), run { count("collaboration_device_sessions") })
        assertEquals(Result.Success(1), run { count("collaboration_devices") })
    }
}
