package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
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
class AttendanceSyncMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun upgradeRetainsEvidenceAndConcurrentChangesShareOneTransactionalHead() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("65").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val other = UUID.randomUUID()
        val account = UUID.randomUUID()
        val employee = UUID.randomUUID()
        val person = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'ATTENDANCE','Attendance upgrade','Asia/Jakarta'),(?,'OTHER','Other company','UTC')",
            company,
            other,
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'attendance-upgrade@example.test','Fixture account')",
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
        val original = UUID.randomUUID()
        insertEvent(sql, company, employee, account, original)
        sql.update(
            "insert into attendance_reviews(company_id,event_id,actor_id,decision,reviewed_at,reason) values(?,?,?,'REJECT',now(),'Previous review')",
            company,
            original,
            account,
        )
        for (date in listOf("2026-09-29", "2026-09-30")) {
            sql.update(
                "insert into attendance_day_versions(company_id,employment_id,work_date,version) values(?,?,?::date,0)",
                company,
                employee,
                date,
            )
            sql.update(
                "insert into attendance_corrections(company_id,id,employment_id,work_date,revision,break_minutes,schedule,actor_id,recorded_at,reason) values(?,?,?,?::date,0,0,'{}'::jsonb,?,now(),'Previous correction')",
                company,
                UUID.randomUUID(),
                employee,
                date,
                account,
            )
        }
        val before = facts(sql)
        val migrationSource =
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
                connectionProperties =
                    Properties().apply { setProperty("ApplicationName", "attendance-upgrade") }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            source.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val writer = JdbcTemplate(SingleConnectionDataSource(connection, true))
                    insertEvent(writer, company, employee, account, UUID.randomUUID(), "2026-09-28")
                    val expected = facts(writer)
                    val writerPid =
                        writer.queryForObject("select pg_backend_pid()", Int::class.java)
                    val migration =
                        pool.submit {
                            Flyway.configure()
                                .dataSource(migrationSource)
                                .target("66")
                                .load()
                                .migrate()
                        }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (
                        sql.queryForObject(
                            "select exists(select 1 from pg_stat_activity where application_name='attendance-upgrade' and wait_event_type='Lock' and ?=any(pg_blocking_pids(pid)))",
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
                "select id,work_date,version from attendance_sync_heads order by work_date"
            )
        assertEquals(3, heads.size)
        assertTrue(heads.all { it["version"] == 0L })
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from mobile_sync_changes where collection='ATTENDANCE_DAYS'",
                Int::class.java,
            ),
        )

        sql.execute("create role attendance_upgrade_runtime login password 'upgrade-fixture-only'")
        sql.execute("grant usage on schema public to attendance_upgrade_runtime")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to attendance_upgrade_runtime"
        )
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "attendance_upgrade_runtime",
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
                        "select count(*) from attendance_sync_heads",
                        Int::class.java,
                    )
                }
            },
        )
        val appended =
            transactions.run(actor) {
                safeDatabaseCall {
                    insertEvent(runtime, company, employee, account, UUID.randomUUID())
                }
            }
        assertTrue(appended is Result.Success, appended.toString())
        val ready = CountDownLatch(2)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                List(2) {
                    pool.submit<Result<Int>> {
                        transactions.run(actor) {
                            ready.countDown()
                            check(ready.await(5, TimeUnit.SECONDS))
                            safeDatabaseCall {
                                insertEvent(runtime, company, employee, account, UUID.randomUUID())
                            }
                        }
                    }
                }
            pending.forEach { assertTrue(it.get(10, TimeUnit.SECONDS) is Result.Success) }
        }
        val updated =
            sql.queryForMap(
                "select id,version from attendance_sync_heads where company_id=? and work_date='2026-09-30'",
                company,
            )
        assertEquals(heads.last()["id"], updated["id"])
        assertEquals(3L, updated["version"])
        assertEquals(
            listOf(1L, 2L, 3L),
            sql.queryForList(
                "select resource_version from mobile_sync_changes where collection='ATTENDANCE_DAYS' order by resource_version",
                Long::class.java,
            ),
        )
        val rollbackId = UUID.randomUUID()
        val rolledBack =
            transactions.run(actor) {
                safeDatabaseCall {
                        insertEvent(runtime, company, employee, account, rollbackId, "2026-09-27")
                    }
                    .flatMap { Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback")) }
            }
        assertTrue(rolledBack is Result.Failed)
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from attendance_events where id=?",
                Int::class.java,
                rollbackId,
            ),
        )
        assertEquals(
            3,
            sql.queryForObject("select count(*) from attendance_sync_heads", Int::class.java),
        )
        assertEquals(
            3,
            sql.queryForObject(
                "select count(*) from mobile_sync_changes where collection='ATTENDANCE_DAYS'",
                Int::class.java,
            ),
        )
        assertEquals(before["attendance_corrections"], facts(sql)["attendance_corrections"])
        assertEquals(before["attendance_day_versions"], facts(sql)["attendance_day_versions"])
    }

    private fun facts(sql: JdbcTemplate): Map<String, List<String>> =
        listOf(
                "attendance_events",
                "attendance_reviews",
                "attendance_corrections",
                "attendance_day_versions",
            )
            .associateWith { table ->
                sql.queryForList(
                        "select to_jsonb(t)::text from $table t order by to_jsonb(t)::text",
                        String::class.java,
                    )
                    .map { requireNotNull(it) }
            }

    private fun insertEvent(
        sql: JdbcTemplate,
        company: UUID,
        employee: UUID,
        account: UUID,
        id: UUID,
        date: String = "2026-09-30",
    ): Int =
        sql.update(
            "insert into attendance_events(company_id,id,employment_id,account_id,work_date,kind,captured_at,received_at,device_id,offline,mocked,schedule,initial_status,issues) values(?,?,?,?,?::date,'CLOCK_IN',now(),now(),?,true,false,'{}'::jsonb,'PENDING','{}'::text[])",
            company,
            id,
            employee,
            account,
            date,
            UUID.randomUUID(),
        )
}
