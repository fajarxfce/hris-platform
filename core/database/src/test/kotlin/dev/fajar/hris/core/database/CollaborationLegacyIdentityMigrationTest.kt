package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
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
class CollaborationLegacyIdentityMigrationTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    private inner class Fixture {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val sql = JdbcTemplate(source)
        val legacyCompany = UUID.randomUUID()
        val currentCompany = UUID.randomUUID()
        val caller = UUID.randomUUID()
        val peer = UUID.randomUUID()
        val oldDevice = UUID.randomUUID()
        val retiredDevice = UUID.randomUUID()
        val peerDevice = UUID.randomUUID()
        val runtimeSource =
            DriverManagerDataSource(postgres.jdbcUrl, "legacy_fixture", "isolated-fixture-only")
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val actor = Actor(caller, null, emptySet(), Instant.now(), UUID.randomUUID())

        init {
            Flyway.configure().dataSource(source).target("117").load().migrate()
            for ((index, company) in listOf(legacyCompany, currentCompany).withIndex()) sql.update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                "LEGACY$index",
                "Fictional company $index",
            )
            for ((index, account) in listOf(caller, peer).withIndex()) {
                sql.update(
                    "insert into accounts(id,email,display_name) values(?,?,?)",
                    account,
                    "legacy-$index@example.test",
                    "Fictional $index",
                )
                sql.update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    currentCompany,
                    account,
                )
                sql.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    currentCompany,
                    account,
                )
            }
            for ((account, device) in
                listOf(caller to oldDevice, caller to retiredDevice, peer to peerDevice)) {
                sql.update(
                    "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional legacy','PROVISIONING',0,now(),now())",
                    legacyCompany,
                    device,
                    account,
                )
                sql.update(
                    "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-never-a-real-token',now(),now(),now()+interval '5 minutes')",
                    legacyCompany,
                    device,
                    account,
                    "@hris_${compact(legacyCompany)}_${compact(account)}:matrix.example.test",
                    "HRIS_${compact(device)}",
                )
                sql.update(
                    "update collaboration_devices set state='ACTIVE',version=1,updated_at=now() where company_id=? and id=?",
                    legacyCompany,
                    device,
                )
            }
            sql.update(
                "update collaboration_devices set state='REVOKED',version=2,revoked_at=now(),updated_at=now() where company_id=? and id=?",
                legacyCompany,
                retiredDevice,
            )
            sql.update(
                "update collaboration_provider_sessions set generation=1,token_encrypted=null,cleared_at=now(),updated_at=now() where company_id=? and device_id=?",
                legacyCompany,
                retiredDevice,
            )
            sql.execute("create role legacy_fixture login password 'isolated-fixture-only'")
            sql.execute("grant usage on schema public to legacy_fixture")
            sql.execute(
                "grant select,insert,update,delete on all tables in schema public to legacy_fixture"
            )
        }

        fun migrate() {
            Flyway.configure().dataSource(source).target("118").load().migrate()
            sql.execute("grant select on collaboration_space_legacy_devices to legacy_fixture")
        }

        fun <T> run(current: Actor = actor, body: () -> T): Result<T> =
            transactions.run(current) { safeDatabaseCall(body) }

        fun rows(current: Actor = actor) =
            run(current) {
                runtime.queryForList(
                    "select * from collaboration_space_legacy_devices order by legacy_device_id"
                )
            }

        fun original() =
            listOf(
                    "collaboration_devices",
                    "collaboration_provider_sessions",
                    "collaboration_device_sessions",
                    "company_memberships",
                    "membership_permissions",
                )
                .associateWith { sql.queryForList("select * from $it order by 1,2") }
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    @Test
    fun ownedInventoryRetainsExactIdentitiesWithoutRequiringTheFormerHrCompany() {
        with(Fixture()) {
            val before = original()
            migrate()
            assertEquals(before, original())
            val owned = success(rows())
            assertEquals(
                setOf(oldDevice, retiredDevice),
                owned.map { it["legacy_device_id"] }.toSet(),
            )
            assertTrue(
                owned.all { it["account_id"] == caller && it["legacy_company_id"] == legacyCompany }
            )
            assertTrue(
                owned.all {
                    it["matrix_user_id"] ==
                        "@hris_${compact(legacyCompany)}_${compact(caller)}:matrix.example.test"
                }
            )
            assertTrue(
                owned.all {
                    it["matrix_device_id"] == "HRIS_${compact(it["legacy_device_id"] as UUID)}"
                }
            )
            assertEquals(setOf("ACTIVE", "REVOKED"), owned.map { it["state"] }.toSet())
            assertNotNull(
                owned.single { it["legacy_device_id"] == retiredDevice }["credential_retired_at"]
            )
            assertEquals(
                0,
                sql.queryForObject(
                    "select count(*) from collaboration_device_sessions",
                    Int::class.java,
                ),
            )
            assertEquals(owned, success(rows(actor.copy(companyId = currentCompany))))
            assertEquals(owned, success(rows(actor.copy(companyId = legacyCompany))))
            assertEquals(
                listOf(peerDevice),
                success(rows(actor.copy(accountId = peer))).map { it["legacy_device_id"] },
            )
            assertTrue(success(rows(actor.copy(accountId = UUID.randomUUID()))).isEmpty())
            assertEquals(
                0,
                success(
                    run {
                        runtime.queryForObject(
                            "select count(*) from collaboration_provider_sessions",
                            Int::class.java,
                        )
                    }
                ),
            )
            assertEquals(
                0,
                success(
                    run {
                        runtime.queryForObject(
                            "select count(*) from company_memberships where company_id=?",
                            Int::class.java,
                            legacyCompany,
                        )
                    }
                ),
            )
            assertTrue(
                owned.all {
                    it.keys.none { key ->
                        key.contains("token") ||
                            key.contains("encrypted") ||
                            key.contains("email") ||
                            key.contains("native_session")
                    }
                }
            )
            sql.update(
                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                currentCompany,
                caller,
            )
            assertTrue(success(rows()).isEmpty())
            assertEquals(
                before["collaboration_provider_sessions"],
                original()["collaboration_provider_sessions"],
            )
        }
    }

    @Test
    fun metadataViewOwnerCannotReadCredentialsOrAssumeRuntimeAuthority() {
        with(Fixture()) {
            migrate()
            val owner = "hris_collaboration_legacy_reader"
            assertEquals(
                listOf(false, false, false, false, false, false),
                sql.queryForList(
                        "select rolcanlogin,rolinherit,rolsuper,rolcreatedb,rolcreaterole,rolbypassrls from pg_roles where rolname=?",
                        owner,
                    )
                    .single()
                    .values
                    .toList(),
            )
            for ((table, column) in
                listOf(
                    "collaboration_provider_sessions" to "token_encrypted",
                    "accounts" to "email",
                    "native_sessions" to "access_hash",
                    "collaboration_gateway_sessions" to "replay_encrypted",
                )) assertEquals(
                false,
                sql.queryForObject(
                    "select has_column_privilege(?,?,?,'SELECT')",
                    Boolean::class.java,
                    owner,
                    table,
                    column,
                ),
            )
            assertEquals(
                false,
                sql.queryForObject(
                    "select pg_has_role('legacy_fixture',?,'MEMBER')",
                    Boolean::class.java,
                    owner,
                ),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run { runtime.execute("set local role hris_collaboration_legacy_reader") },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run { runtime.update("delete from collaboration_space_legacy_devices") },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.queryForList(
                        "select token_encrypted from collaboration_space_legacy_devices"
                    )
                },
            )
        }
    }

    @Test
    fun archiveRestoresMetadataOwnershipAndProviderRowsWithoutRekeying() {
        with(Fixture()) {
            migrate()
            val before = original()
            val owned = success(rows())
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
                "/tmp/legacy.dump",
            )
            command("createdb", "-U", postgres.username, "legacy_restored")
            command(
                "pg_restore",
                "-U",
                postgres.username,
                "-d",
                "legacy_restored",
                "--single-transaction",
                "--exit-on-error",
                "/tmp/legacy.dump",
            )
            val restoredUrl = postgres.jdbcUrl.substringBeforeLast('/') + "/legacy_restored"
            val restored =
                JdbcTemplate(
                    DriverManagerDataSource(restoredUrl, postgres.username, postgres.password)
                )
            assertEquals(
                "hris_collaboration_legacy_reader",
                restored.queryForObject(
                    "select viewowner from pg_views where schemaname='public' and viewname='collaboration_space_legacy_devices'",
                    String::class.java,
                ),
            )
            for ((table, rows) in before) assertEquals(
                rows,
                restored.queryForList("select * from $table order by 1,2"),
                table,
            )
            val restrictedSource =
                DriverManagerDataSource(restoredUrl, "legacy_fixture", "isolated-fixture-only")
            val restricted = JdbcTemplate(restrictedSource)
            val runner =
                PostgresTransactionRunner(JdbcTransactionManager(restrictedSource), restricted)
            assertEquals(
                owned,
                success(
                    runner.run(actor) {
                        safeDatabaseCall {
                            restricted.queryForList(
                                "select * from collaboration_space_legacy_devices order by legacy_device_id"
                            )
                        }
                    }
                ),
            )
        }
    }
}
