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
class RosterResetMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun upgradeRetainsOverridesAndHistoryWhileResetAndReactivationPublishOrderedVersions() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("86").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val other = UUID.randomUUID()
        val account = UUID.randomUUID()
        val employee = UUID.randomUUID()
        val person = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'ROSTER','Roster upgrade','Asia/Jakarta'),(?,'OTHER','Other company','UTC')",
            company,
            other,
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'roster-upgrade@example.test','Fixture account')",
            account,
        )
        sql.update(
            "insert into persons(id,owner_company_id,legal_name,nationality) values(?,?,'Fixture employee','ID')",
            person,
            company,
        )
        sql.update(
            "insert into employments(company_id,id,person_id,employee_number) values(?,?,?,'E001')",
            company,
            employee,
            person,
        )
        sql.update(
            "insert into roster_days(company_id,employment_id,work_date,version) values(?,?,'2026-10-02',0)",
            company,
            employee,
        )
        sql.update(
            "insert into roster_revisions(company_id,employment_id,work_date,revision,actor_id,reason) values(?,?,'2026-10-02',0,?,'Original day off')",
            company,
            employee,
            account,
        )
        val original =
            sql.queryForObject("select to_jsonb(r)::text from roster_days r", String::class.java)
        val history =
            sql.queryForObject(
                "select to_jsonb(r)::text from roster_revisions r",
                String::class.java,
            )
        val id = sql.queryForObject("select id from roster_days", UUID::class.java)
        val changes =
            sql.queryForObject(
                "select count(*) from mobile_sync_changes where collection='ROSTER_DAYS'",
                Int::class.java,
            )
        Flyway.configure().dataSource(source).target("87").load().migrate()
        assertEquals(
            original,
            sql.queryForObject(
                "select (to_jsonb(r)-'active')::text from roster_days r",
                String::class.java,
            ),
        )
        assertEquals(
            history,
            sql.queryForObject(
                "select (to_jsonb(r)-'active')::text from roster_revisions r",
                String::class.java,
            ),
        )
        assertEquals(
            true,
            sql.queryForObject("select active from roster_days", Boolean::class.java),
        )
        assertEquals(
            true,
            sql.queryForObject("select active from roster_revisions", Boolean::class.java),
        )
        assertEquals(
            changes,
            sql.queryForObject(
                "select count(*) from mobile_sync_changes where collection='ROSTER_DAYS'",
                Int::class.java,
            ),
        )

        sql.execute("create role roster_reset_runtime login password 'upgrade-fixture-only'")
        sql.execute("grant usage on schema public to roster_reset_runtime")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to roster_reset_runtime"
        )
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "roster_reset_runtime",
                "upgrade-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val actor = Actor(account, company, emptySet(), Instant.now(), UUID.randomUUID())
        assertEquals(
            Result.Success(0),
            tx.run(actor.copy(companyId = other)) {
                safeDatabaseCall {
                    runtime.queryForObject("select count(*) from roster_days", Int::class.java)
                }
            },
        )
        assertTrue(
            tx.run(actor) {
                safeDatabaseCall {
                    runtime.update(
                        "update roster_days set active=false,shift=null,version=1 where id=?",
                        id,
                    )
                    runtime.update(
                        "insert into roster_revisions(company_id,employment_id,work_date,revision,active,actor_id,reason) values(?,?,'2026-10-02',1,false,?,'Restore calendar')",
                        company,
                        employee,
                        account,
                    )
                }
            } is Result.Success
        )
        assertEquals(id, sql.queryForObject("select id from roster_days", UUID::class.java))
        assertEquals(
            listOf(true, false),
            sql.queryForList(
                "select active from roster_revisions order by revision",
                Boolean::class.java,
            ),
        )
        assertEquals(
            listOf("UPSERT", "DELETE"),
            sql.queryForList(
                "select operation from mobile_sync_changes where collection='ROSTER_DAYS' order by resource_version",
                String::class.java,
            ),
        )

        assertTrue(
            tx.run(actor) {
                safeDatabaseCall {
                        runtime.update(
                            "update roster_days set active=true,version=2 where id=?",
                            id,
                        )
                    }
                    .flatMap { Result.Failed(Failure(FailureKind.CONFLICT, "owned_rollback")) }
            } is Result.Failed
        )
        assertEquals(1L, sql.queryForObject("select version from roster_days", Long::class.java))
        assertEquals(
            2,
            sql.queryForObject(
                "select count(*) from mobile_sync_changes where collection='ROSTER_DAYS'",
                Int::class.java,
            ),
        )
        assertTrue(
            tx.run(actor) {
                safeDatabaseCall {
                    runtime.update("update roster_days set active=true,version=2 where id=?", id)
                }
            } is Result.Success
        )
        assertEquals(id, sql.queryForObject("select id from roster_days", UUID::class.java))
        assertEquals(
            listOf(0L, 1L, 2L),
            sql.queryForList(
                "select resource_version from mobile_sync_changes where collection='ROSTER_DAYS' order by resource_version",
                Long::class.java,
            ),
        )
        assertEquals(
            listOf("UPSERT", "DELETE", "UPSERT"),
            sql.queryForList(
                "select operation from mobile_sync_changes where collection='ROSTER_DAYS' order by resource_version",
                String::class.java,
            ),
        )
        assertThrows(Exception::class.java) {
            sql.execute("update roster_days set active=false,shift='{}'::jsonb")
        }
        assertThrows(Exception::class.java) {
            sql.execute("update roster_revisions set active=false")
        }
        assertThrows(Exception::class.java) { sql.execute("delete from roster_revisions") }
    }
}
