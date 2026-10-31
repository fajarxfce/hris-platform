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
class CollaborationDirectMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun upgradeAndDumpRestorePreserveCanonicalPairsAndParticipantIsolation() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("109").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val second = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val outsider = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'DIRECT','Fictional direct company','UTC')",
            company,
        )
        for (id in listOf(first, second, outsider)) sql.update(
            "insert into accounts(id,email,display_name) values(?,?,'Fictional direct participant')",
            id,
            "$id@example.test",
        )
        val accounts =
            sql.queryForList("select row_to_json(a)::text as snapshot from accounts a order by id")
        Flyway.configure().dataSource(source).target("110").load().migrate()
        assertEquals(
            accounts,
            sql.queryForList("select row_to_json(a)::text as snapshot from accounts a order by id"),
        )
        sql.execute("create role direct_fixture login password 'direct-fixture-only'")
        sql.execute("grant usage on schema public to direct_fixture")
        sql.execute(
            "grant select,insert,update,delete on collaboration_direct_conversations to direct_fixture"
        )
        val runtimeSource =
            DriverManagerDataSource(postgres.jdbcUrl, "direct_fixture", "direct-fixture-only")
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val actor = Actor(first, company, emptySet(), Instant.now(), UUID.randomUUID())
        val id = UUID.randomUUID()
        assertEquals(
            Result.Success(1),
            transactions.run(actor) {
                Result.Success(
                    runtime.update(
                        "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,now())",
                        company,
                        id,
                        first,
                        second,
                        first,
                    )
                )
            },
        )
        val expected =
            sql.queryForList(
                "select row_to_json(c)::text as snapshot from collaboration_direct_conversations c"
            )
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
            "/tmp/direct.dump",
        )
        command("createdb", "-U", postgres.username, "direct_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "direct_restored",
            "--exit-on-error",
            "/tmp/direct.dump",
        )
        val url =
            postgres.jdbcUrl.substringBefore('?').substringBeforeLast('/') + "/direct_restored"
        val restoredOwner =
            JdbcTemplate(DriverManagerDataSource(url, postgres.username, postgres.password))
        assertEquals(
            expected,
            restoredOwner.queryForList(
                "select row_to_json(c)::text as snapshot from collaboration_direct_conversations c"
            ),
        )
        val restoredSource = DriverManagerDataSource(url, "direct_fixture", "direct-fixture-only")
        val restored = JdbcTemplate(restoredSource)
        val restoredTransactions =
            PostgresTransactionRunner(JdbcTransactionManager(restoredSource), restored)
        for ((reader, count) in
            listOf(
                actor to 1,
                actor.copy(accountId = second) to 1,
                actor.copy(accountId = outsider) to 0,
                actor.copy(companyId = UUID.randomUUID()) to 0,
            )) {
            assertEquals(
                Result.Success(count),
                restoredTransactions.run(reader) {
                    Result.Success(
                        restored.queryForObject(
                            "select count(*) from collaboration_direct_conversations",
                            Int::class.java,
                        )
                    )
                },
            )
        }
        assertEquals(
            Result.Success(0),
            restoredTransactions.run(actor) {
                Result.Success(
                    restored.update("delete from collaboration_direct_conversations where id=?", id)
                )
            },
        )
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            restoredOwner.update("delete from collaboration_direct_conversations where id=?", id)
        }
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            restoredOwner.update(
                "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,now())",
                company,
                UUID.randomUUID(),
                first,
                second,
                first,
            )
        }
        assertEquals(
            0,
            restored.queryForObject(
                "select count(*) from collaboration_direct_conversations",
                Int::class.java,
            ),
        )
    }
}
