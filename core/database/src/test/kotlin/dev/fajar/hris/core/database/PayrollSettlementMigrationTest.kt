package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class PayrollSettlementMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun upgradeWaitsForPublicationAndPreservesOriginalPaymentsVersionsAndScopedBalances() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("76").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val other = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val foreign = UUID.randomUUID()
        // Thin historical records isolate upgrade/backfill behavior. Runtime publication
        // and bank reconciliation are exercised through complete API fixtures separately.
        TransactionTemplate(DataSourceTransactionManager(source)).executeWithoutResult {
            sql.execute("set local session_replication_role=replica")
            retained(sql, company, first, paid = true)
            retained(sql, other, foreign, paid = false)
        }
        val migrationSource =
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
                connectionProperties =
                    Properties().apply {
                        setProperty("ApplicationName", "payroll-settlement-upgrade")
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
                    retained(writer, company, second, paid = false)
                    val expected = retainedEvidence(writer)
                    val pid = writer.queryForObject("select pg_backend_pid()", Int::class.java)
                    val migration =
                        pool.submit {
                            Flyway.configure()
                                .dataSource(migrationSource)
                                .target("78")
                                .load()
                                .migrate()
                        }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
                    while (
                        sql.queryForObject(
                            "select exists(select 1 from pg_stat_activity where application_name='payroll-settlement-upgrade' and wait_event_type='Lock' and ?=any(pg_blocking_pids(pid)))",
                            Boolean::class.java,
                            pid,
                        ) != true
                    ) {
                        check(!migration.isDone) { "Upgrade did not wait for publication" }
                        check(System.nanoTime() < deadline) {
                            "Upgrade did not acquire its publication guard"
                        }
                        Thread.sleep(10)
                    }
                    connection.commit()
                    migration.get(20, TimeUnit.SECONDS)
                    assertEquals(expected, retainedEvidence(sql))
                } finally {
                    connection.rollback()
                }
            }
        }
        assertEquals(
            3,
            sql.queryForObject("select count(*) from payroll_assessment_holidays", Int::class.java),
        )
        assertEquals(
            0,
            sql.queryForObject("select count(*) from mobile_sync_changes", Int::class.java),
        )
        assertEquals(
            0,
            sql.queryForObject("select count(*) from payroll_payment_recoveries", Int::class.java),
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from payroll_payment_items where assessment_revision<>0 or observed_payment_version is not null",
                Int::class.java,
            ),
        )
        sql.execute(
            "create role settlement_upgrade_runtime login password 'upgrade-fixture-only' nosuperuser nobypassrls"
        )
        sql.execute("grant usage on schema public to settlement_upgrade_runtime")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to settlement_upgrade_runtime"
        )
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "settlement_upgrade_runtime",
                "upgrade-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val transactions =
            PostgresTransactionRunner(DataSourceTransactionManager(runtimeSource), runtime)
        val actor = Actor(UUID.randomUUID(), company, emptySet(), Instant.now(), UUID.randomUUID())
        val result =
            transactions.run(actor) {
                safeDatabaseCall {
                    runtime.queryForList(
                        "select assessment_id,assessment_revision,payment_version,entitlement,paid_amount,recovered_amount,balance from payroll_settlement_balances order by assessment_id"
                    )
                }
            }
        assertTrue(result is Result.Success, result.toString())
        val balances = (result as Result.Success).value
        assertEquals(setOf(first, second), balances.map { it["assessment_id"] }.toSet())
        val paid = balances.single { it["assessment_id"] == first }
        assertEquals(3L, paid["payment_version"])
        assertEquals("0.00", paid["balance"].toString())
        assertEquals("100.00", paid["paid_amount"].toString())
        assertEquals(0L, balances.single { it["assessment_id"] == second }["payment_version"])
        assertEquals(
            Result.Success(2),
            transactions.run(actor) {
                Result.Success(
                    runtime.queryForObject(
                        "select count(*) from payroll_assessment_holidays",
                        Int::class.java,
                    )
                )
            },
        )
    }

    private fun retained(sql: JdbcTemplate, company: UUID, assessment: UUID, paid: Boolean) {
        val person = UUID.randomUUID()
        val employee = UUID.randomUUID()
        val run = UUID.randomUUID()
        sql.update(
            "insert into persons(id,owner_company_id,legal_name,nationality) values(?,?,'Historical employee','ID')",
            person,
            company,
        )
        sql.update(
            "insert into payroll_run_targets(company_id,run_id,period_id,ordinal,employment_id,employee_number,employee_name,employment_version) values(?,?,?,1,?,'E001','Historical employee',0)",
            company,
            run,
            UUID.randomUUID(),
            employee,
        )
        sql.update(
            "insert into payroll_run_results(company_id,run_id,ordinal,job_id,status,completed_at,facts,calculation,taxable_gross,withheld,take_home) values(?,?,1,?,'SUCCEEDED','2026-10-01T08:00:00Z','{}','{\"tax\":{\"takeHome\":\"100.00\"}}',100,0,100)",
            company,
            run,
            UUID.randomUUID(),
        )
        sql.update(
            "insert into payroll_assessments(company_id,id,finalization_id,run_id,ordinal,employment_id,person_id,tax_month,published_at,holiday_kind,holiday_year) values(?,?,?,?,1,?,?,'2026-09-01','2026-10-01T08:00:00Z','CHRISTMAS',2026)",
            company,
            assessment,
            UUID.randomUUID(),
            run,
            employee,
            person,
        )
        sql.update(
            "insert into payroll_assessment_revisions(company_id,assessment_id,revision,published_at) values(?,?,0,'2026-10-01T08:00:00Z')",
            company,
            assessment,
        )
        sql.update(
            "insert into payroll_assessment_heads(company_id,assessment_id,revision) values(?,?,0)",
            company,
            assessment,
        )
        if (paid) {
            sql.update(
                "insert into payroll_payment_items(company_id,id,batch_id,assessment_id,employment_id,employee_number,employee_name,tax_month,planned_payment_date,amount,bank_code,account_number,account_name,status,version,batch_version,transaction_reference,occurred_at) values(?,?,?,?,?,'E001','Historical employee','2026-09-01','2026-10-02',100,'TEST','12345678','Historical employee','SUCCEEDED',2,2,'PAID:historical','2026-10-02T08:00:00Z')",
                company,
                UUID.randomUUID(),
                UUID.randomUUID(),
                assessment,
                employee,
            )
            sql.update(
                "insert into payroll_payment_heads(company_id,assessment_id,employment_id,version) values(?,?,?,3)",
                company,
                assessment,
                employee,
            )
        }
    }

    private fun retainedEvidence(sql: JdbcTemplate) =
        listOf(
                "payroll_assessments",
                "payroll_assessment_revisions",
                "payroll_assessment_heads",
                "payroll_run_targets",
                "payroll_run_results",
                "payroll_payment_items",
                "payroll_payment_heads",
            )
            .associateWith { table ->
                sql.queryForList(
                    "select (to_jsonb(t)-ARRAY['amendment_publication_id','assessment_revision','observed_payment_version'])::text from $table t order by 1",
                    String::class.java,
                )
            }
}
