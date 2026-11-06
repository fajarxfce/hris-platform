package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
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
class CollaborationSpaceDirectoryMigrationTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    private class Fixture(val source: DriverManagerDataSource) {
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val otherCompany = UUID.randomUUID()
        val secondCompany = UUID.randomUUID()
        val inactiveCompany = UUID.randomUUID()
        val caller = UUID.randomUUID()
        val peer = UUID.randomUUID()
        val noChat = UUID.randomUUID()
        val inactiveAccount = UUID.randomUUID()
        val inactiveMember = UUID.randomUUID()
        val inactiveEmployer = UUID.randomUUID()
        val device = UUID.randomUUID()
        val runtimeSource =
            DriverManagerDataSource(source.url!!, "space_fixture", "isolated-fixture-only")
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val actor = Actor(caller, company, emptySet(), Instant.now(), UUID.randomUUID())

        fun <T> run(current: Actor = actor, body: () -> T): Result<T> =
            transactions.run(current) { safeDatabaseCall(body) }

        fun contacts(current: Actor = actor): Result<List<Map<String, Any?>>> =
            run(current) {
                runtime.queryForList(
                    "select * from collaboration_space_contacts order by account_id,company_id"
                )
            }

        fun space(current: Actor = actor): Result<List<UUID>> =
            run(current) {
                runtime
                    .queryForList(
                        "select space_id from collaboration_space_directory",
                        UUID::class.java,
                    )
                    .map { checkNotNull(it) }
            }

        fun migrate(target: String) {
            Flyway.configure().dataSource(source).target(target).load().migrate()
        }
    }

    private fun fixture(): Fixture {
        val fixture =
            Fixture(DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password))
        with(fixture) {
            migrate("116")
            for ((index, id) in
                listOf(company, otherCompany, secondCompany, inactiveCompany).withIndex()) {
                sql.update(
                    "insert into companies(id,code,name,timezone,active) values(?,?,?,'UTC',?)",
                    id,
                    "SPACE$index",
                    "Fictional company $index",
                    id != inactiveCompany,
                )
            }
            for ((index, id) in
                listOf(caller, peer, noChat, inactiveAccount, inactiveMember, inactiveEmployer)
                    .withIndex()) {
                sql.update(
                    "insert into accounts(id,email,display_name,active) values(?,?,?,?)",
                    id,
                    "space-$index@example.test",
                    "Fictional contact $index",
                    id != inactiveAccount,
                )
            }
            for ((employer, account) in
                listOf(
                    company to caller,
                    secondCompany to caller,
                    otherCompany to peer,
                    secondCompany to peer,
                    otherCompany to noChat,
                    otherCompany to inactiveAccount,
                    otherCompany to inactiveMember,
                    inactiveCompany to inactiveEmployer,
                )) {
                sql.update(
                    "insert into company_memberships(company_id,account_id,active) values(?,?,?)",
                    employer,
                    account,
                    account != inactiveMember,
                )
                sql.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    employer,
                    account,
                    if (account == noChat) "company.read" else "collaboration.chat",
                )
            }
            sql.update(
                "insert into persons(id,owner_company_id,account_id,legal_name,nationality,email) values(?,?,?,'Private legal name','ID','private-profile@example.test')",
                UUID.randomUUID(),
                otherCompany,
                peer,
            )
            sql.update(
                "insert into oidc_identities(issuer,subject,account_id) values('https://identity.example.test','stable-fictional-subject',?)",
                caller,
            )
            sql.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Retained fixture','PROVISIONING',0,now(),now())",
                company,
                device,
                caller,
            )
        }
        return fixture
    }

    private fun Fixture.enableRuntime() {
        migrate("117")
        sql.execute("create role space_fixture login password 'isolated-fixture-only'")
        sql.execute("grant usage on schema public to space_fixture")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to space_fixture"
        )
    }

    @Test
    fun migrationRetainsIdentityAndCompanyGrantsWhilePublishingOnlyEligibleContactLabels() {
        with(fixture()) {
            val original =
                listOf(
                        "company_memberships",
                        "membership_permissions",
                        "oidc_identities",
                        "collaboration_devices",
                    )
                    .associateWith { sql.queryForList("select * from $it order by 1,2") }
            enableRuntime()
            original.forEach { (table, rows) ->
                assertEquals(rows, sql.queryForList("select * from $table order by 1,2"), table)
            }
            val spaceId =
                sql.queryForObject("select id from collaboration_spaces", UUID::class.java)!!
            assertFalse(spaceId in listOf(company, otherCompany, secondCompany, inactiveCompany))
            assertEquals(Result.Success(listOf(spaceId)), space())
            migrate("117")
            assertEquals(
                Result.Success(listOf(spaceId)),
                space(actor.copy(companyId = secondCompany)),
            )
            assertEquals(Result.Success(listOf(spaceId)), space(actor.copy(companyId = null)))
            val contacts = (contacts() as Result.Success).value
            assertEquals(4, contacts.size)
            assertEquals(setOf(caller, peer), contacts.map { it["account_id"] }.toSet())
            assertEquals(
                setOf(otherCompany, secondCompany),
                contacts.filter { it["account_id"] == peer }.map { it["company_id"] }.toSet(),
            )
            assertTrue(
                contacts.all {
                    it.keys ==
                        setOf(
                            "space_id",
                            "account_id",
                            "display_name",
                            "company_id",
                            "company_label",
                        )
                }
            )
            assertEquals(Result.Success(contacts), contacts(actor.copy(companyId = secondCompany)))
            for (table in listOf("persons", "company_memberships", "membership_permissions")) {
                val owner = if (table == "persons") "owner_company_id" else "company_id"
                assertEquals(
                    Result.Success(0),
                    run {
                        runtime.queryForObject(
                            "select count(*) from $table where $owner=?",
                            Int::class.java,
                            otherCompany,
                        )
                    },
                    table,
                )
            }
            assertEquals(
                Result.Success(0),
                run {
                    runtime.queryForObject(
                        "select count(*) from companies where id=?",
                        Int::class.java,
                        otherCompany,
                    )
                },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.update(
                        "insert into company_memberships(company_id,account_id) values(?,?)",
                        otherCompany,
                        caller,
                    )
                },
            )
            // Preserve the singleton identity and restricted view owner in a real archive restore.
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
                "/tmp/space.dump",
            )
            command("createdb", "-U", postgres.username, "space_restored")
            command(
                "pg_restore",
                "-U",
                postgres.username,
                "-d",
                "space_restored",
                "--single-transaction",
                "--exit-on-error",
                "/tmp/space.dump",
            )
            val restoredSource =
                DriverManagerDataSource(
                    postgres.jdbcUrl.substringBeforeLast('/') + "/space_restored",
                    "space_fixture",
                    "isolated-fixture-only",
                )
            val restored = JdbcTemplate(restoredSource)
            val restoredTransactions =
                PostgresTransactionRunner(JdbcTransactionManager(restoredSource), restored)
            assertEquals(
                Result.Success(contacts),
                restoredTransactions.run(actor.copy(companyId = null)) {
                    safeDatabaseCall {
                        restored.queryForList(
                            "select * from collaboration_space_contacts order by account_id,company_id"
                        )
                    }
                },
            )
            assertEquals(
                "hris_collaboration_directory_reader",
                restored.queryForObject(
                    "select relowner::regrole::text from pg_class where oid='collaboration_space_contacts'::regclass",
                    String::class.java,
                ),
            )
        }
    }

    @Test
    fun eligibilityFollowsCommittedOffboardingAndAccountReplacementWithoutCompanySelectionCache() {
        with(fixture()) {
            enableRuntime()
            val initial = contacts()
            for (account in
                listOf(
                    noChat,
                    inactiveAccount,
                    inactiveMember,
                    inactiveEmployer,
                    UUID.randomUUID(),
                )) {
                assertEquals(
                    Result.Success(emptyList<UUID>()),
                    space(actor.copy(accountId = account)),
                )
                assertEquals(
                    Result.Success(emptyList<Map<String, Any?>>()),
                    contacts(actor.copy(accountId = account)),
                )
            }
            sql.update(
                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                company,
                caller,
            )
            assertEquals(
                Result.Success(3),
                run {
                    runtime.queryForObject(
                        "select count(*) from collaboration_space_contacts",
                        Int::class.java,
                    )
                },
            )
            assertFalse((space() as Result.Success).value.isEmpty())
            source.connection.use { writer ->
                writer.autoCommit = false
                writer
                    .prepareStatement(
                        "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?"
                    )
                    .use {
                        it.setObject(1, secondCompany)
                        it.setObject(2, caller)
                        assertEquals(1, it.executeUpdate())
                    }
                // An independent read observes committed eligibility until this offboarding
                // commits.
                assertFalse((space() as Result.Success).value.isEmpty())
                writer.commit()
            }
            assertEquals(Result.Success(emptyList<UUID>()), space())
            assertEquals(Result.Success(emptyList<Map<String, Any?>>()), contacts())
            sql.update(
                "update company_memberships set active=true,version=version+1 where account_id=?",
                caller,
            )
            assertEquals(initial, contacts())
            sql.update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                peer,
            )
            assertEquals(
                Result.Success(0),
                run {
                    runtime.queryForObject(
                        "select count(*) from collaboration_space_contacts where account_id=?",
                        Int::class.java,
                        peer,
                    )
                },
            )
            sql.update("update accounts set active=false,version=version+1 where id=?", caller)
            assertEquals(Result.Success(emptyList<UUID>()), space())
            // Transaction-local context must not leave a directory available to an unscoped
            // connection.
            assertEquals(
                0,
                runtime.queryForObject(
                    "select count(*) from collaboration_space_contacts",
                    Int::class.java,
                ),
            )
        }
    }

    @Test
    fun viewOwnerCannotReadPrivateColumnsAndRuntimeCannotAcquireItsRoleOrMutateTheSpace() {
        with(fixture()) {
            enableRuntime()
            val reader = "hris_collaboration_directory_reader"
            for (column in
                listOf("email", "password_hash", "mfa_secret_encrypted", "security_version")) {
                assertEquals(
                    false,
                    sql.queryForObject(
                        "select has_column_privilege(?,'accounts',?,'SELECT')",
                        Boolean::class.java,
                        reader,
                        column,
                    ),
                    column,
                )
            }
            for (table in
                listOf(
                    "persons",
                    "native_sessions",
                    "oidc_identities",
                    "collaboration_devices",
                    "collaboration_device_key_publications",
                )) {
                assertEquals(
                    false,
                    sql.queryForObject(
                        "select has_table_privilege(?,?,'SELECT')",
                        Boolean::class.java,
                        reader,
                        table,
                    ),
                    table,
                )
            }
            val flags =
                sql.queryForMap(
                    "select rolcanlogin,rolsuper,rolinherit,rolcreaterole,rolcreatedb,rolbypassrls from pg_roles where rolname=?",
                    reader,
                )
            assertTrue(flags.values.all { it == false })
            assertThrows(DataAccessException::class.java) {
                runtime.execute("set role hris_collaboration_directory_reader")
            }
            assertEquals(
                Result.Success(0),
                run {
                    runtime.queryForObject(
                        "select count(*) from collaboration_spaces",
                        Int::class.java,
                    )
                },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run { runtime.update("insert into collaboration_spaces default values") },
            )
            assertEquals(
                Result.Success(0),
                run { runtime.update("delete from collaboration_spaces") },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.update(
                        "update collaboration_space_contacts set display_name='Unauthorized change'"
                    )
                },
            )
            assertThrows(DataAccessException::class.java) {
                sql.update("update collaboration_spaces set id=?", UUID.randomUUID())
            }
            assertThrows(DataAccessException::class.java) {
                sql.update("delete from collaboration_spaces")
            }
            assertEquals(
                1,
                sql.queryForObject("select count(*) from collaboration_spaces", Int::class.java),
            )
        }
    }
}
