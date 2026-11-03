package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceIdentityMigrationTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    private inner class Fixture {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val otherCompany = UUID.randomUUID()
        val account = UUID.randomUUID()
        val peer = UUID.randomUUID()
        val newcomer = UUID.randomUUID()
        val firstDevice = UUID.randomUUID()
        val otherDevice = UUID.randomUUID()
        val space: UUID
        val runtimeSource =
            DriverManagerDataSource(postgres.jdbcUrl, "selection_fixture", "isolated-fixture-only")
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val actor = Actor(account, null, emptySet(), Instant.now(), UUID.randomUUID())

        init {
            Flyway.configure().dataSource(source).target("118").load().migrate()
            space =
                requireNotNull(
                    sql.queryForObject("select id from collaboration_spaces", UUID::class.java)
                )
            for ((index, id) in listOf(company, otherCompany).withIndex()) sql.update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                id,
                "SELECT$index",
                "Fictional $index",
            )
            for ((index, id) in listOf(account, peer, newcomer).withIndex()) {
                sql.update(
                    "insert into accounts(id,email,display_name) values(?,?,?)",
                    id,
                    "selection-$index@example.test",
                    "Fictional $index",
                )
                sql.update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    otherCompany,
                    id,
                )
                sql.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    otherCompany,
                    id,
                )
            }
            for ((employer, device) in
                listOf(company to firstDevice, otherCompany to otherDevice)) {
                sql.update(
                    "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,now(),now())",
                    employer,
                    device,
                    account,
                )
                sql.update(
                    "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-not-a-real-token',now(),now(),now()+interval '5 minutes')",
                    employer,
                    device,
                    account,
                    legacyUser(employer, account),
                    "HRIS_" + compact(device),
                )
                sql.update(
                    "update collaboration_devices set state='ACTIVE',version=1,updated_at=now() where company_id=? and id=?",
                    employer,
                    device,
                )
            }
        }

        fun migrate() {
            Flyway.configure().dataSource(source).target("119").load().migrate()
            sql.execute("create role selection_fixture login password 'isolated-fixture-only'")
            sql.execute("grant usage on schema public to selection_fixture")
            sql.execute(
                "grant select,insert,update,delete on all tables in schema public to selection_fixture"
            )
        }

        fun <T> run(current: Actor = actor, body: () -> T): Result<T> =
            transactions.run(current) { safeDatabaseCall(body) }

        fun insert(
            selectedAccount: UUID = account,
            selectedSpace: UUID = space,
            employer: UUID? = company,
            device: UUID? = firstDevice,
            origin: String = "LEGACY",
            user: String = legacyUser(company, account),
            server: String = "matrix.example.test",
        ) =
            runtime.update(
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,legacy_company_id,legacy_device_id,created_at) values(?,?,?,?,?,?,?,now())",
                selectedSpace,
                selectedAccount,
                server,
                user,
                origin,
                employer,
                device,
            )

        fun rows(current: Actor = actor) =
            run(current) { runtime.queryForList("select * from collaboration_space_identities") }

        fun providerRows() =
            sql.queryForList(
                "select * from collaboration_provider_sessions order by company_id,device_id"
            )
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun legacyUser(company: UUID, account: UUID) =
        "@hris_${compact(company)}_${compact(account)}:matrix.example.test"

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    @Test
    fun explicitLegacySelectionSurvivesRetirementAndRestoreWithoutChangingItsProviderIdentity() {
        with(Fixture()) {
            val original = providerRows()
            migrate()
            assertEquals(original, providerRows())
            assertTrue(success(rows()).isEmpty())
            assertEquals(1, success(run { insert() }))
            val chosen = success(rows())
            assertEquals(legacyUser(company, account), chosen.single()["matrix_user_id"])
            assertEquals(chosen, success(rows(actor.copy(companyId = otherCompany))))
            assertEquals(chosen, success(rows(actor.copy(companyId = company))))
            assertEquals(
                0,
                success(
                    run {
                        runtime.update(
                            "update collaboration_space_identities set matrix_user_id='@other:matrix.example.test'"
                        )
                    }
                ),
            )
            assertEquals(
                0,
                success(run { runtime.update("delete from collaboration_space_identities") }),
            )
            assertThrows(DataAccessException::class.java) {
                sql.update("delete from collaboration_space_identities")
            }
            sql.update(
                "update collaboration_devices set state='REVOKED',version=2,revoked_at=now(),updated_at=now() where company_id=? and id=?",
                company,
                firstDevice,
            )
            sql.update(
                "update collaboration_provider_sessions set generation=1,token_encrypted=null,cleared_at=now(),updated_at=now() where company_id=? and device_id=?",
                company,
                firstDevice,
            )
            assertEquals(chosen, success(rows()))
            val retained = providerRows()
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
                "/tmp/selection.dump",
            )
            command("createdb", "-U", postgres.username, "selection_restored")
            command(
                "pg_restore",
                "-U",
                postgres.username,
                "-d",
                "selection_restored",
                "--single-transaction",
                "--exit-on-error",
                "/tmp/selection.dump",
            )
            val restoredUrl = postgres.jdbcUrl.substringBeforeLast('/') + "/selection_restored"
            val restored =
                JdbcTemplate(
                    DriverManagerDataSource(restoredUrl, postgres.username, postgres.password)
                )
            assertEquals(
                retained,
                restored.queryForList(
                    "select * from collaboration_provider_sessions order by company_id,device_id"
                ),
            )
            val limited =
                DriverManagerDataSource(restoredUrl, "selection_fixture", "isolated-fixture-only")
            val limitedSql = JdbcTemplate(limited)
            val runner = PostgresTransactionRunner(JdbcTransactionManager(limited), limitedSql)
            assertEquals(
                chosen,
                success(
                    runner.run(actor) {
                        safeDatabaseCall {
                            limitedSql.queryForList("select * from collaboration_space_identities")
                        }
                    }
                ),
            )
            assertThrows(DataAccessException::class.java) {
                restored.update("delete from collaboration_space_identities")
            }
        }
    }

    @Test
    fun nativeAccountScopeAndExactOriginCannotBeForgedOrReadByAnotherEmployee() {
        with(Fixture()) {
            migrate()
            for (attempt in
                listOf<() -> Result<*>>(
                    { run(actor.copy(companyId = company)) { insert() } },
                    { run(actor.copy(accountId = peer)) { insert() } },
                    { run { insert(selectedSpace = UUID.randomUUID()) } },
                    { run { insert(device = UUID.randomUUID()) } },
                    { run { insert(user = legacyUser(otherCompany, account)) } },
                    { run { insert(server = "other.example.test") } },
                    { run { insert(origin = "SPACE") } },
                    { run { insert(employer = null, device = null) } },
                )) assertInstanceOf(Result.Failed::class.java, attempt())
            assertTrue(success(rows()).isEmpty())
            assertEquals(1, success(run { insert() }))
            assertTrue(success(rows(actor.copy(accountId = peer))).isEmpty())
            assertEquals(
                1,
                success(
                    run(actor.copy(accountId = newcomer)) {
                        insert(
                            selectedAccount = newcomer,
                            employer = null,
                            device = null,
                            origin = "SPACE",
                            user =
                                "@hrisg_${compact(space)}_${compact(newcomer)}:matrix.example.test",
                        )
                    }
                ),
            )
            sql.update(
                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                otherCompany,
                account,
            )
            assertTrue(success(rows()).isEmpty())
            assertEquals(
                2,
                sql.queryForObject(
                    "select count(*) from collaboration_space_identities",
                    Int::class.java,
                ),
            )
        }
    }

    @Test
    fun competingSelectionsRetainOneCanonicalUserWithoutMutatingEitherLegacyUser() {
        with(Fixture()) {
            migrate()
            val original = providerRows()
            val barrier = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val attempts =
                    listOf(company to firstDevice, otherCompany to otherDevice)
                        .map { (employer, device) ->
                            pool.submit<Result<Int>> {
                                run {
                                    barrier.await(5, TimeUnit.SECONDS)
                                    insert(
                                        employer = employer,
                                        device = device,
                                        user = legacyUser(employer, account),
                                    )
                                }
                            }
                        }
                        .map { it.get(15, TimeUnit.SECONDS) }
                assertEquals(1, attempts.count { it is Result.Success })
                assertEquals(1, attempts.count { it is Result.Failed })
                assertEquals(1, success(rows()).size)
                assertTrue(
                    success(rows()).single()["matrix_user_id"] in
                        setOf(legacyUser(company, account), legacyUser(otherCompany, account))
                )
                assertEquals(original, providerRows())
            } finally {
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }
}
