package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import java.time.ZoneOffset
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["spring.flyway.target=67", MOBILE_SYNC_TEST_KEYS])
class ExpensePaymentSyncMigrationHttpTest : ExpensePaymentApiFixture() {
    @Test
    fun backfillWaitsForAnExistingPaymentWriterAndRetainsEvidenceAndCompanyIsolation() {
        val f = expenseFixture()
        expenseTemplate(f)
        val submission = approvedExpense(f)
        val maker = payer(f)
        val checker = payer(f)
        val cancelled = UUID.randomUUID()
        assertEquals(
            200,
            preparePayment(f, maker, cancelled, listOf(paymentInstruction(submission))).statusCode(),
        )
        assertEquals(200, paymentAction(f, maker, cancelled, "cancel", 0).statusCode())
        val batch = UUID.randomUUID()
        assertEquals(
            200,
            preparePayment(f, maker, batch, listOf(paymentInstruction(submission))).statusCode(),
        )
        val other = expenseFixture()
        expenseTemplate(other)
        val otherSubmission = approvedExpense(other)
        val otherMaker = payer(other)
        val otherChecker = payer(other)
        val otherBatch = UUID.randomUUID()
        val otherItem = UUID.randomUUID()
        assertEquals(
            200,
            preparePayment(
                    other,
                    otherMaker,
                    otherBatch,
                    listOf(paymentInstruction(otherSubmission, otherItem)),
                )
                .statusCode(),
        )
        assertEquals(200, paymentAction(other, otherChecker, otherBatch, "release", 0).statusCode())
        assertEquals(
            200,
            reconcilePayment(other, otherMaker, otherBatch, 1, listOf(paymentResult(otherItem)))
                .statusCode(),
        )

        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val migrationSource =
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
                connectionProperties =
                    Properties().apply { setProperty("ApplicationName", "expense-payment-upgrade") }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            source.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val writer =
                        JdbcTemplate(SingleConnectionDataSource(connection, true)).apply {
                            queryTimeout = 5
                        }
                    writer.queryForObject(
                        "select set_config('hris.actor_id',?,true)",
                        String::class.java,
                        checker.account.toString(),
                    )
                    val releasedAt = clock.instant().atOffset(ZoneOffset.UTC)
                    writer.update(
                        "update expense_payment_batches set status='RELEASED',version=1,released_by=?,released_at=? where company_id=? and id=?",
                        checker.account,
                        releasedAt,
                        f.company,
                        batch,
                    )
                    writer.update(
                        "update expense_payment_items set status='PENDING',version=1,batch_version=1 where company_id=? and batch_id=?",
                        f.company,
                        batch,
                    )
                    writer.update(
                        "insert into expense_payment_actions(company_id,batch_id,version,kind,status,actor_id,reason,recorded_at) values(?,?,1,'RELEASED','RELEASED',?,'Existing writer',?)",
                        f.company,
                        batch,
                        checker.account,
                        releasedAt,
                    )
                    val expected = facts(writer)
                    val writerPid =
                        writer.queryForObject("select pg_backend_pid()", Int::class.java)
                    val migration =
                        pool.submit {
                            Flyway.configure()
                                .dataSource(migrationSource)
                                .target("68")
                                .load()
                                .migrate()
                        }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (
                        database()
                            .queryForObject(
                                "select exists(select 1 from pg_stat_activity where application_name='expense-payment-upgrade' and wait_event_type='Lock' and ?=any(pg_blocking_pids(pid)))",
                                Boolean::class.java,
                                writerPid,
                            ) != true
                    ) {
                        check(!migration.isDone) {
                            "Migration must wait for the existing payment writer"
                        }
                        check(System.nanoTime() < deadline) {
                            "Migration did not reach its schema guard"
                        }
                        Thread.sleep(10)
                    }
                    connection.commit()
                    migration.get(15, TimeUnit.SECONDS)
                    assertEquals(expected, facts(database()))
                } finally {
                    connection.rollback()
                }
            }
        }
        val heads =
            database()
                .queryForList(
                    "select company_id,claim_id,employment_id,version from expense_payment_heads"
                )
        assertEquals(2, heads.size)
        assertEquals(4L, heads.single { it["company_id"] == f.company }["version"])
        assertEquals(3L, heads.single { it["company_id"] == other.company }["version"])
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from mobile_sync_changes where collection='EXPENSE_PAYMENTS'",
                    Int::class.java,
                ),
        )
        // Backfill assertions above deliberately exercise V68. Runtime endpoints
        // below must see all subsequent migrations used by the current application.
        Flyway.configure().dataSource(source).load().migrate()
        val progress = get(f.worker, "${f.path}/${f.claim}/payment-progress")
        assertEquals(200, progress.statusCode(), progress.body())
        assertEquals(4L, json.readTree(progress.body())["version"].asLong())
        assertEquals(2, json.readTree(progress.body())["attempts"].size())
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        assertTrue(worker.maintain.execute() is Result.Success)
        val bootstrap =
            get(
                f.worker,
                "/api/v1/companies/${f.company}/sync/bootstrap?collections=EXPENSE_PAYMENTS",
            )
        assertEquals(200, bootstrap.statusCode(), bootstrap.body())
        val resources = json.readTree(bootstrap.body())["items"]
        assertEquals(1, resources.size())
        assertEquals(f.claim.toString(), resources[0]["id"].asString())
        assertEquals(4L, resources[0]["version"].asLong())

        val runtimeSource =
            DriverManagerDataSource(postgres.jdbcUrl, "hris_test_runtime", "test-runtime-only")
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val actor =
            Actor(checker.account, other.company, emptySet(), clock.instant(), UUID.randomUUID())
        assertEquals(
            Result.Success(0),
            transactions.run(actor) {
                safeDatabaseCall {
                    runtime.queryForObject(
                        "select count(*) from expense_payment_heads where claim_id=?",
                        Int::class.java,
                        f.claim,
                    )
                }
            },
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "delete from expense_payment_heads where company_id=? and claim_id=?",
                    f.company,
                    f.claim,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update expense_payment_heads set version=version+1 where company_id=? and claim_id=?",
                    f.company,
                    f.claim,
                )
        }
        val item = UUID.fromString(paymentDetails(f, maker, batch)["items"][0]["id"].asString())
        val settled = reconcilePayment(f, maker, batch, 1, listOf(paymentResult(item)))
        assertEquals(200, settled.statusCode(), settled.body())
        assertEquals(
            5L,
            database()
                .queryForObject(
                    "select version from expense_payment_heads where company_id=? and claim_id=?",
                    Long::class.java,
                    f.company,
                    f.claim,
                ),
        )
        val captured =
            database()
                .queryForMap(
                    "select company_id,resource_id,employment_id,resource_version from mobile_sync_changes where collection='EXPENSE_PAYMENTS'"
                )
        assertEquals(f.company, captured["company_id"])
        assertEquals(f.claim, captured["resource_id"])
        assertEquals(f.employee, captured["employment_id"])
        assertEquals(5L, captured["resource_version"])
    }

    private fun facts(sql: JdbcTemplate) =
        listOf(
                "expense_claims",
                "expense_drafts",
                "expense_submissions",
                "expense_payment_batches",
                "expense_payment_items",
                "expense_payment_actions",
                "expense_payment_results",
                "mobile_sync_changes",
            )
            .associateWith { table ->
                sql.queryForList(
                    "select to_jsonb(t)::text as evidence from $table t order by to_jsonb(t)::text"
                )
            }
}
