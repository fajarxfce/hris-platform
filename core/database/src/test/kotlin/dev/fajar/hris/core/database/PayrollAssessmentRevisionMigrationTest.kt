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
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class PayrollAssessmentRevisionMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun upgradeBackfillsHistoricalReferencesAfterAnExistingWriterWithoutRewritingEvidence() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("74").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val other = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val foreign = UUID.randomUUID()
        // Seed only historical reference metadata. The original payroll-generation
        // graph is outside this migration test; HTTP tests finalize real reviewed runs.
        // Replication mode is transaction-local on the isolated privileged fixture,
        // never on the migration or restricted runtime connections below.
        TransactionTemplate(JdbcTransactionManager(source)).executeWithoutResult {
            sql.execute("set local session_replication_role=replica")
            reference(sql, company, first, "2026-09-01", null)
            reference(sql, other, foreign, "2026-09-01", null)
        }
        val migrationSource =
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
                connectionProperties =
                    Properties().apply {
                        setProperty("ApplicationName", "payroll-assessment-upgrade")
                        setProperty("options", "-c statement_timeout=20000 -c lock_timeout=15000")
                    }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            source.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val writer =
                        JdbcTemplate(SingleConnectionDataSource(connection, true)).apply {
                            queryTimeout = 5
                        }
                    writer.execute("set local session_replication_role=replica")
                    reference(writer, company, second, "2026-10-01", first)
                    val expected = evidence(writer)
                    val writerPid =
                        writer.queryForObject("select pg_backend_pid()", Int::class.java)
                    val migration =
                        pool.submit {
                            Flyway.configure()
                                .dataSource(migrationSource)
                                .target("75")
                                .load()
                                .migrate()
                        }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (
                        sql.queryForObject(
                            "select exists(select 1 from pg_stat_activity where application_name='payroll-assessment-upgrade' and wait_event_type='Lock' and ?=any(pg_blocking_pids(pid)))",
                            Boolean::class.java,
                            writerPid,
                        ) != true
                    ) {
                        check(!migration.isDone) {
                            "Upgrade did not wait for the existing publication"
                        }
                        check(System.nanoTime() < deadline) {
                            "Upgrade did not acquire its schema guard"
                        }
                        Thread.sleep(10)
                    }
                    connection.commit()
                    migration.get(20, TimeUnit.SECONDS)
                    assertEquals(expected, evidence(sql))
                } finally {
                    connection.rollback()
                }
            }
        }
        assertEquals(
            3,
            sql.queryForObject("select count(*) from payroll_assessment_revisions", Int::class.java),
        )
        assertEquals(
            3,
            sql.queryForObject("select count(*) from payroll_assessment_heads", Int::class.java),
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from payroll_assessment_revisions v join payroll_assessments a on a.company_id=v.company_id and a.id=v.assessment_id where v.revision<>0 or v.amendment_calculation_id is not null or v.published_at<>a.published_at",
                Int::class.java,
            ),
        )
        assertEquals(
            first,
            sql.queryForObject(
                "select previous_assessment_id from payroll_run_targets where previous_assessment_id is not null",
                UUID::class.java,
            ),
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from payroll_run_targets where previous_assessment_revision<>0",
                Int::class.java,
            ),
        )
        assertEquals(
            0,
            sql.queryForObject("select count(*) from mobile_sync_changes", Int::class.java),
        )
        sql.execute(
            "create role assessment_upgrade_runtime login password 'upgrade-fixture-only' nosuperuser nobypassrls"
        )
        sql.execute("grant usage on schema public to assessment_upgrade_runtime")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to assessment_upgrade_runtime"
        )
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "assessment_upgrade_runtime",
                "upgrade-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val actor = Actor(UUID.randomUUID(), company, emptySet(), Instant.now(), UUID.randomUUID())
        for (table in listOf("payroll_assessment_heads", "payroll_assessment_revisions")) {
            assertEquals(
                Result.Success(2),
                transactions.run(actor) {
                    safeDatabaseCall {
                        runtime.queryForObject("select count(*) from $table", Int::class.java)
                    }
                },
            )
            assertEquals(
                Result.Success(0),
                transactions.run(actor.copy(companyId = other)) {
                    safeDatabaseCall {
                        runtime.queryForObject(
                            "select count(*) from $table where company_id=?",
                            Int::class.java,
                            company,
                        )
                    }
                },
            )
            assertEquals(0, runtime.queryForObject("select count(*) from $table", Int::class.java))
        }
        val mutation =
            transactions.run(actor) {
                safeDatabaseCall {
                    runtime.update(
                        "update payroll_assessment_revisions set published_at=now() where assessment_id=?",
                        first,
                    )
                }
            }
        assertTrue(mutation is Result.Failed, mutation.toString())
        assertEquals(FailureKind.FORBIDDEN, (mutation as Result.Failed).failure.kind)
    }

    private fun reference(
        sql: JdbcTemplate,
        company: UUID,
        assessment: UUID,
        month: String,
        previous: UUID?,
    ) {
        val run = UUID.randomUUID()
        val employee = UUID.randomUUID()
        sql.update(
            "insert into payroll_run_targets(company_id,run_id,period_id,ordinal,employment_id,employee_number,employee_name,employment_version,previous_assessment_id) values(?,?,?,1,?,'E001','Historical fixture',0,?)",
            company,
            run,
            UUID.randomUUID(),
            employee,
            previous,
        )
        sql.update(
            "insert into payroll_assessments(company_id,id,finalization_id,run_id,ordinal,employment_id,person_id,tax_month,published_at) values(?,?,?,?,1,?,?,?::date,'2026-10-01T08:00:00Z')",
            company,
            assessment,
            UUID.randomUUID(),
            run,
            employee,
            UUID.randomUUID(),
            month,
        )
    }

    private fun evidence(sql: JdbcTemplate) =
        listOf("payroll_assessments", "payroll_run_targets").associateWith { table ->
            sql.queryForList(
                "select (to_jsonb(t)-'previous_assessment_revision')::text from $table t order by (to_jsonb(t)-'previous_assessment_revision')::text",
                String::class.java,
            )
        }
}
