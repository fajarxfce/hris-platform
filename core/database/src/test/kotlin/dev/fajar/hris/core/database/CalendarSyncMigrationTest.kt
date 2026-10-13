package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CalendarSyncMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun upgradeRetainsFrozenRulesAndSerializesBackfillWithExistingWriters() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("66").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val other = UUID.randomUUID()
        val account = UUID.randomUUID()
        val employee = UUID.randomUUID()
        val person = UUID.randomUUID()
        val holiday = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'CALENDAR','Calendar upgrade','Asia/Jakarta'),(?,'OTHER','Other company','UTC')",
            company,
            other,
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'calendar-upgrade@example.test','Fixture account')",
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
            "insert into schedule_versions(company_id,employment_id,version) values(?,?,2)",
            company,
            employee,
        )
        assignment(sql, company, employee, account, 0, "2026-09-01")
        assignment(sql, company, employee, account, 1, "2026-09-01")
        assignment(sql, company, employee, account, 2, "2026-10-01")
        sql.update(
            "insert into roster_days(company_id,employment_id,work_date,version) values(?,?,'2026-09-30',0)",
            company,
            employee,
        )
        sql.update(
            "insert into work_holidays(company_id,id,work_date,name) values(?,?,'2026-10-02','Previous holiday')",
            company,
            holiday,
        )
        val migrationSource =
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
                connectionProperties =
                    Properties().apply { setProperty("ApplicationName", "calendar-upgrade") }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            source.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val writer = JdbcTemplate(SingleConnectionDataSource(connection, true))
                    writer.update(
                        "update schedule_versions set version=3 where company_id=? and employment_id=?",
                        company,
                        employee,
                    )
                    assignment(writer, company, employee, account, 3, "2026-09-15")
                    val expected = facts(writer)
                    val writerPid =
                        writer.queryForObject("select pg_backend_pid()", Int::class.java)
                    val migration =
                        pool.submit {
                            Flyway.configure()
                                .dataSource(migrationSource)
                                .target("67")
                                .load()
                                .migrate()
                        }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (
                        sql.queryForObject(
                            "select exists(select 1 from pg_stat_activity where application_name='calendar-upgrade' and wait_event_type='Lock' and ?=any(pg_blocking_pids(pid)))",
                            Boolean::class.java,
                            writerPid,
                        ) != true
                    ) {
                        check(!migration.isDone) {
                            "Migration did not wait for the existing writer"
                        }
                        check(System.nanoTime() < deadline) {
                            "Migration did not acquire its guard"
                        }
                        Thread.sleep(10)
                    }
                    connection.commit()
                    migration.get(15, TimeUnit.SECONDS)
                    assertEquals(expected, facts(sql))
                } finally {
                    connection.rollback()
                }
            }
        }
        val heads =
            sql.queryForList(
                "select id,revision from work_schedule_sync_heads order by effective_from"
            )
        assertEquals(listOf(1L, 3L, 2L), heads.map { it["revision"] })
        val rosterId = sql.queryForObject("select id from roster_days", UUID::class.java)
        assertNotNull(rosterId)
        assertEquals(0, countChanges(sql))
        sql.execute("create role calendar_upgrade_runtime login password 'upgrade-fixture-only'")
        sql.execute("grant usage on schema public to calendar_upgrade_runtime")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to calendar_upgrade_runtime"
        )
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "calendar_upgrade_runtime",
                "upgrade-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val actor = Actor(account, company, emptySet(), Instant.now(), UUID.randomUUID())
        assertEquals(
            Result.Success(0),
            transactions.run(actor.copy(companyId = other)) {
                safeDatabaseCall {
                    runtime.queryForObject(
                        "select count(*) from work_schedule_sync_heads",
                        Int::class.java,
                    )
                }
            },
        )
        val changed =
            transactions.run(actor) {
                safeDatabaseCall {
                    runtime.update(
                        "update schedule_versions set version=4 where company_id=? and employment_id=?",
                        company,
                        employee,
                    )
                    assignment(runtime, company, employee, account, 4, "2026-09-01")
                    runtime.update(
                        "update roster_days set version=1 where company_id=? and employment_id=?",
                        company,
                        employee,
                    )
                    runtime.update(
                        "update work_holidays set active=false,version=1 where company_id=? and id=?",
                        company,
                        holiday,
                    )
                }
            }
        assertTrue(changed is Result.Success, changed.toString())
        assertEquals(
            heads.first()["id"],
            sql.queryForObject(
                "select id from work_schedule_sync_heads where effective_from='2026-09-01'",
                UUID::class.java,
            ),
        )
        assertEquals(
            4L,
            sql.queryForObject(
                "select revision from work_schedule_sync_heads where effective_from='2026-09-01'",
                Long::class.java,
            ),
        )
        assertEquals(rosterId, sql.queryForObject("select id from roster_days", UUID::class.java))
        assertEquals(3, countChanges(sql))
        val shared =
            sql.queryForMap(
                "select employment_id,owner_account_id,resource_version from mobile_sync_changes where collection='WORK_HOLIDAYS'"
            )
        assertNull(shared["employment_id"])
        assertNull(shared["owner_account_id"])
        assertEquals(1L, shared["resource_version"])
        val beforeRollback = facts(sql)
        val rollback =
            transactions.run(actor) {
                safeDatabaseCall {
                        assignment(runtime, company, employee, account, 5, "2026-11-01")
                    }
                    .flatMap { Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback")) }
            }
        assertTrue(rollback is Result.Failed)
        assertEquals(beforeRollback, facts(sql))
        assertEquals(3, countChanges(sql))
        assertEquals(
            3,
            sql.queryForObject("select count(*) from work_schedule_sync_heads", Int::class.java),
        )
        assertThrows(Exception::class.java) {
            sql.update(
                "insert into mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation) values(?,'WORK_HOLIDAYS',?,?,0,'UPSERT')",
                company,
                holiday,
                employee,
            )
        }
    }

    private fun assignment(
        sql: JdbcTemplate,
        company: UUID,
        employee: UUID,
        actor: UUID,
        version: Long,
        from: String,
    ): Int =
        sql.update(
            "insert into schedule_assignments(company_id,employment_id,revision,effective_from,days,actor_id,reason) values(?,?,?,?::date,'{\"days\":{}}'::jsonb,?,'Frozen rule')",
            company,
            employee,
            version,
            from,
            actor,
        )

    private fun countChanges(sql: JdbcTemplate): Int? =
        sql.queryForObject(
            "select count(*) from mobile_sync_changes where collection in ('WORK_SCHEDULES','ROSTER_DAYS','WORK_HOLIDAYS')",
            Int::class.java,
        )

    private fun facts(sql: JdbcTemplate): Map<String, List<String>> =
        listOf("schedule_assignments", "schedule_versions", "roster_days", "work_holidays")
            .associateWith { table ->
                sql.queryForList(
                        "select (to_jsonb(t)-'id')::text from $table t order by (to_jsonb(t)-'id')::text",
                        String::class.java,
                    )
                    .map { requireNotNull(it) }
            }
}
