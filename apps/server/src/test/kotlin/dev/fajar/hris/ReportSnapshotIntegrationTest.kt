package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.ReportSnapshotRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ReportSnapshotIntegrationTest : ApiIntegrationTest() {
    @Autowired private lateinit var snapshots: ReportSnapshotRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var sources: ReportSourceTransactionRunner
    @Autowired private lateinit var sql: DSLContext

    private fun fixture(): Actor {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,'Snapshot company','UTC')",
                company,
                "S${company.toString().take(8)}",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Snapshot account')",
                account,
                "$account@example.test",
            )
        return Actor(account, company, emptySet(), Instant.now(), UUID.randomUUID())
    }

    private fun capture(actor: Actor, expired: Boolean = false): ReportSnapshotCapture {
        val at =
            Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(if (expired) 172800 else 0)
        return (validateReportSnapshotCapture(
                ReportSnapshotCapture(
                    UUID.randomUUID(),
                    requireNotNull(actor.companyId),
                    actor.accountId,
                    ReportSelection(
                        ReportKind.HEADCOUNT,
                        "headcount.v1",
                        ReportFilterParameters(asOf = "2026-10-01"),
                    ),
                    ReportExportDataset.SUMMARY,
                    listOf(
                        ReportExportColumn("label", ReportExportColumnType.TEXT),
                        ReportExportColumn("count", ReportExportColumnType.INTEGER),
                        ReportExportColumn("amount", ReportExportColumnType.DECIMAL),
                        ReportExportColumn("optional", ReportExportColumnType.TEXT, true),
                    ),
                    at,
                    at.plusSeconds(86400),
                )
            )
                as Result.Success)
            .value
    }

    private fun row(index: Int): List<ReportExportCell> =
        listOf(
            ReportExportCell.Text("=SUM(1,2) — 測試 $index"),
            ReportExportCell.Integer(Long.MIN_VALUE + index),
            ReportExportCell.Decimal(BigDecimal("-1234567890123456789012.12345678")),
            ReportExportCell.Empty,
        )

    private fun store(
        actor: Actor,
        c: ReportSnapshotCapture,
        rows: List<List<ReportExportCell>>,
    ): Result<ReportSnapshot> =
        transactions.run(actor) {
            snapshots.begin(c).flatMap { fresh ->
                assertTrue(fresh)
                var after = 0
                for (page in rows.chunked(500)) {
                    val appended = snapshots.append(c, after, page)
                    if (appended is Result.Failed) return@flatMap appended
                    after += page.size
                }
                snapshots.seal(c)
            }
        }

    @Test
    fun capturedRowsSurviveNewTransactionsWithExactValuesOrderedPagesAndOriginalSchema() {
        val actor = fixture()
        val c = capture(actor)
        val expected = List(1251, ::row)
        val stored = (store(actor, c, expected) as Result.Success).value
        assertEquals(1251, stored.rows)
        assertTrue(stored.storedBytes in 1..REPORT_SNAPSHOT_MAX_BYTES)
        val retained =
            (transactions.run(actor) { snapshots.find(c.companyId, c.accountId, c.id) }
                    as Result.Success)
                .value!!
        assertEquals(stored, retained)
        var after = 0
        val actual = mutableListOf<List<ReportExportCell>>()
        while (after < retained.rows) {
            assertTrue(after < 2000)
            val page =
                (transactions.run(actor) { snapshots.rows(retained, after, 500) } as Result.Success)
                    .value
            assertTrue(page.isNotEmpty() && page.size <= 500)
            assertEquals((after + 1..after + page.size).toList(), page.map { it.number })
            actual.addAll(page.map { it.cells })
            after += page.size
            assertThrows(UnsupportedOperationException::class.java) {
                (page.first().cells as MutableList).clear()
            }
        }
        assertEquals(expected, actual)
        assertEquals(
            Result.Success(emptyList<ReportSnapshotRow>()),
            transactions.run(actor) { snapshots.rows(retained, 1251, 500) },
        )
        assertEquals(
            Result.Success(false),
            transactions.run(actor) {
                snapshots.begin(c.copy(expiresAt = c.expiresAt.plusSeconds(1)))
            },
        )
        assertEquals(
            Result.Success(stored),
            transactions.run(actor) { snapshots.find(c.companyId, c.accountId, c.id) },
        )
        val empty =
            (store(actor, c.copy(id = UUID.randomUUID()), emptyList()) as Result.Success).value
        assertEquals(0, empty.rows)
        assertEquals(0L, empty.storedBytes)
        assertEquals(
            Result.Success(emptyList<ReportSnapshotRow>()),
            transactions.run(actor) { snapshots.rows(empty, 0, 500) },
        )
    }

    @Test
    fun longUnicodeRowsUseAByteBoundedPrefixWithoutLosingTheRemainder() {
        val actor = fixture()
        val c =
            capture(actor)
                .copy(columns = listOf(ReportExportColumn("label", ReportExportColumnType.TEXT)))
        val expected = List(300) { listOf(ReportExportCell.Text("測".repeat(4090) + "-$it")) }
        val stored =
            (transactions.run(actor) {
                    snapshots.begin(c).flatMap {
                        var after = 0
                        for (page in expected.chunked(50)) {
                            val append = snapshots.append(c, after, page)
                            if (append is Result.Failed) return@flatMap append
                            after += page.size
                        }
                        snapshots.seal(c)
                    }
                } as Result.Success)
                .value
        val actual = mutableListOf<List<ReportExportCell>>()
        var after = 0
        var pages = 0
        while (after < stored.rows) {
            assertTrue(pages++ < 10)
            val page =
                (transactions.run(actor) { snapshots.rows(stored, after, 500) } as Result.Success)
                    .value
            assertTrue(page.isNotEmpty() && page.size < 300)
            assertTrue(
                page.sumOf {
                    (it.cells.single() as ReportExportCell.Text)
                        .value
                        .toByteArray(Charsets.UTF_8)
                        .size + 4
                } <= REPORT_SNAPSHOT_PAGE_BYTES
            )
            actual.addAll(page.map { it.cells })
            after += page.size
        }
        assertEquals(expected, actual)
        assertTrue(pages >= 4)
    }

    @Test
    fun companyAndAccountIsolationApplyToHeadersRowsAndCleanup() {
        val owner = fixture()
        val other = fixture()
        val c = capture(owner, true)
        val stored = (store(owner, c, listOf(row(0))) as Result.Success).value
        for (intruder in
            listOf(
                other,
                other.copy(companyId = owner.companyId),
                owner.copy(companyId = other.companyId),
            )) {
            assertEquals(
                Result.Success(null),
                transactions.run(intruder) { snapshots.find(c.companyId, c.accountId, c.id) },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(intruder) { snapshots.rows(stored, 0, 1) },
            )
            assertEquals(
                Result.Success(0),
                transactions.run(intruder) {
                    snapshots.deleteExpiredRows(c.companyId, c.accountId, c.id, Instant.now(), 500)
                },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(intruder) { snapshots.begin(c.copy(id = UUID.randomUUID())) },
            )
            assertEquals(
                "access_denied",
                (transactions.run(intruder) { snapshots.append(c, 1, listOf(row(1))) }
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
        val sameCompany = other.copy(companyId = owner.companyId)
        assertEquals(
            "data_conflict",
            (transactions.run(sameCompany) { snapshots.begin(capture(sameCompany).copy(id = c.id)) }
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            1,
            (transactions.run(owner) { snapshots.rows(stored, 0, 1) } as Result.Success).value.size,
        )
    }

    @Test
    fun unsealedGappedFailedAndInterruptedCapturesCannotCommitAnyRows() {
        val actor = fixture()
        for (mode in listOf("unsealed", "gap", "failure", "interrupt")) {
            val c = capture(actor)
            val action = {
                transactions.run(actor) {
                    snapshots.begin(c).flatMap {
                        snapshots.append(c, if (mode == "gap") 1 else 0, listOf(row(0))).flatMap {
                            if (mode == "unsealed") return@flatMap Result.Success(Unit)
                            snapshots.seal(c).flatMap {
                                if (mode == "failure")
                                    Result.Failed(Failure(FailureKind.CONFLICT, "capture_rejected"))
                                else {
                                    if (mode == "interrupt") Thread.currentThread().interrupt()
                                    Result.Success(Unit)
                                }
                            }
                        }
                    }
                }
            }
            if (mode == "interrupt") {
                try {
                    assertThrows(InterruptedException::class.java) { action() }
                } finally {
                    Thread.interrupted()
                }
            } else assertInstanceOf(Result.Failed::class.java, action(), mode)
            assertEquals(
                Result.Success(null),
                transactions.run(actor) { snapshots.find(c.companyId, c.accountId, c.id) },
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from report_snapshot_rows where snapshot_id=?",
                        Int::class.java,
                        c.id,
                    ),
            )
        }
    }

    @Test
    fun publicationAndSnapshotIdentityCannotBeRewrittenAndLiveRowsCannotBeDeleted() {
        val actor = fixture()
        val c = capture(actor)
        val stored = (store(actor, c, listOf(row(0))) as Result.Success).value
        val edits =
            listOf(
                "update report_snapshots set expires_at=expires_at+interval '1 second' where id=?",
                "update report_snapshots set definition='{}' where id=?",
                "update report_snapshots set staged_bytes=staged_bytes+1 where id=?",
                "delete from report_snapshots where id=?",
                "update report_snapshot_rows set cells='[]' where snapshot_id=?",
                "delete from report_snapshot_rows where snapshot_id=?",
            )
        for (edit in edits) assertInstanceOf(
            Result.Failed::class.java,
            transactions.run(actor) { safeDatabaseCall { sql.execute(edit, c.id) } },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            transactions.run(actor) { snapshots.append(c, 1, listOf(row(1))) },
        )
        assertEquals(
            Result.Success(0),
            transactions.run(actor) {
                snapshots.deleteExpiredRows(
                    c.companyId,
                    c.accountId,
                    c.id,
                    c.expiresAt.plusSeconds(86400),
                    500,
                )
            },
        )
        assertEquals(
            Result.Success(stored),
            transactions.run(actor) { snapshots.find(c.companyId, c.accountId, c.id) },
        )
    }

    @Test
    fun expiredRowsRetireInFiniteBatchesWhileTheOriginalIdentityAndDeadlineRemain() {
        val actor = fixture()
        val c = capture(actor, true)
        val stored = (store(actor, c, List(1001, ::row)) as Result.Success).value
        assertEquals(
            Result.Success(0),
            transactions.run(actor) {
                snapshots.deleteExpiredRows(
                    c.companyId,
                    c.accountId,
                    c.id,
                    c.expiresAt.minusSeconds(1),
                    500,
                )
            },
        )
        val cancelled = Result.Failed(Failure(FailureKind.CONFLICT, "cleanup_cancelled"))
        assertEquals(
            cancelled,
            transactions.run(actor) {
                snapshots
                    .deleteExpiredRows(c.companyId, c.accountId, c.id, Instant.now(), 500)
                    .flatMap { cancelled }
            },
        )
        for (expected in listOf(500, 500, 1, 0)) assertEquals(
            Result.Success(expected),
            transactions.run(actor) {
                snapshots.deleteExpiredRows(c.companyId, c.accountId, c.id, Instant.now(), 500)
            },
        )
        assertEquals(
            Result.Success(stored),
            transactions.run(actor) { snapshots.find(c.companyId, c.accountId, c.id) },
        )
        assertEquals(Result.Success(false), transactions.run(actor) { snapshots.begin(c) })
        assertInstanceOf(
            Result.Failed::class.java,
            transactions.run(actor) { snapshots.append(c, 0, listOf(row(0))) },
        )
    }

    @Test
    fun competingExpiryBatchesDeleteDisjointRowsAndTerminate() {
        val actor = fixture()
        val c = capture(actor, true)
        assertInstanceOf(Result.Success::class.java, store(actor, c, List(1001, ::row)))
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val tasks =
                (0..1).map {
                    pool.submit<Result<Int>> {
                        ready.countDown()
                        check(start.await(10, TimeUnit.SECONDS))
                        transactions.run(actor) {
                            snapshots.deleteExpiredRows(
                                c.companyId,
                                c.accountId,
                                c.id,
                                Instant.now(),
                                500,
                            )
                        }
                    }
                }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(
                listOf(500, 500),
                tasks.map { (it.get(15, TimeUnit.SECONDS) as Result.Success).value },
            )
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
        assertEquals(
            Result.Success(1),
            transactions.run(actor) {
                snapshots.deleteExpiredRows(c.companyId, c.accountId, c.id, Instant.now(), 500)
            },
        )
        assertEquals(
            Result.Success(0),
            transactions.run(actor) {
                snapshots.deleteExpiredRows(c.companyId, c.accountId, c.id, Instant.now(), 500)
            },
        )
    }

    @Test
    fun competingCaptureAttemptsRetainExactlyOneOriginalResult() {
        val actor = fixture()
        val c = capture(actor)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val attempts =
                (0..1).map { index ->
                    pool.submit<Result<Boolean>> {
                        ready.countDown()
                        check(start.await(10, TimeUnit.SECONDS))
                        transactions.run(actor) {
                            snapshots.begin(c).flatMap { fresh ->
                                if (!fresh) Result.Success(false)
                                else
                                    snapshots.append(c, 0, listOf(row(index))).flatMap {
                                        snapshots.seal(c).map { true }
                                    }
                            }
                        }
                    }
                }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val outcomes = attempts.map { (it.get(15, TimeUnit.SECONDS) as Result.Success).value }
            assertEquals(1, outcomes.count { it })
            val stored =
                (transactions.run(actor) { snapshots.find(c.companyId, c.accountId, c.id) }
                        as Result.Success)
                    .value!!
            assertEquals(
                row(outcomes.indexOf(true)),
                (transactions.run(actor) { snapshots.rows(stored, 0, 1) } as Result.Success)
                    .value
                    .single()
                    .cells,
            )
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun aSingleSqlSourceCaptureIsRetainedAfterTheSourceChanges() {
        val actor = fixture()
        val c =
            capture(actor)
                .copy(
                    columns =
                        listOf(ReportExportColumn("company_name", ReportExportColumnType.TEXT))
                )
        val stored =
            (sources.runWithReportSources(actor, setOf(c.companyId)) {
                    snapshots.begin(c).flatMap {
                        safeDatabaseCall {
                                sql.execute(
                                    """insert into report_snapshot_rows(company_id,account_id,snapshot_id,row_number,cells)
                    select ?,?,?,1,jsonb_build_array(name) from companies where id=?""",
                                    c.companyId,
                                    c.accountId,
                                    c.id,
                                    c.companyId,
                                )
                            }
                            .flatMap { snapshots.seal(c) }
                    }
                } as Result.Success)
                .value
        database()
            .update("update companies set name='Changed after capture' where id=?", c.companyId)
        assertEquals(
            listOf(ReportExportCell.Text("Snapshot company")),
            (transactions.run(actor) { snapshots.rows(stored, 0, 1) } as Result.Success)
                .value
                .single()
                .cells,
        )
    }

    @Test
    fun rowAndByteQuotasRejectOversizeSqlCaptureWithoutLeavingAPartialSnapshot() {
        val actor = fixture()
        for (oversize in listOf(false, true)) {
            val c =
                capture(actor)
                    .copy(columns = listOf(ReportExportColumn("text", ReportExportColumnType.TEXT)))
            val result =
                transactions.run(actor) {
                    snapshots.begin(c).flatMap {
                        safeDatabaseCall {
                                if (oversize)
                                    sql.execute(
                                        """insert into report_snapshot_rows(company_id,account_id,snapshot_id,row_number,cells)
                    select ?,?,?,n,jsonb_build_array(repeat('x',4096)) from generate_series(1,25600) n""",
                                        c.companyId,
                                        c.accountId,
                                        c.id,
                                    )
                                else
                                    sql.execute(
                                        "insert into report_snapshot_rows(company_id,account_id,snapshot_id,row_number,cells) values(?,?,?,100001,'[\"x\"]')",
                                        c.companyId,
                                        c.accountId,
                                        c.id,
                                    )
                            }
                            .flatMap { snapshots.seal(c) }
                    }
                }
            assertEquals("data_conflict", (result as Result.Failed).failure.code)
            assertEquals(
                Result.Success(null),
                transactions.run(actor) { snapshots.find(c.companyId, c.accountId, c.id) },
            )
        }
    }

    @Test
    fun sevenElapsedDaysRemainExactAcrossDatabaseDaylightSavingTransitions() {
        val actor = fixture()
        for (at in
            listOf(Instant.parse("2026-03-05T05:00:00Z"), Instant.parse("2026-10-29T04:00:00Z"))) {
            val c = capture(actor).copy(evaluatedAt = at, expiresAt = at.plusSeconds(604800))
            val stored =
                transactions.run(actor) {
                    safeDatabaseCall { sql.execute("set local time zone 'America/New_York'") }
                        .flatMap { snapshots.begin(c).flatMap { snapshots.seal(c) } }
                }
            assertEquals(c, (stored as Result.Success).value.capture)
            val bad = UUID.randomUUID()
            assertEquals(
                "data_conflict",
                (transactions.run(actor) {
                        safeDatabaseCall {
                            sql.execute("set local time zone 'America/New_York'")
                            sql.execute(
                                """insert into report_snapshots(company_id,account_id,id,definition,columns,evaluated_at,expires_at)
                    select company_id,account_id,?,definition,columns,evaluated_at,expires_at+interval '1 second'
                    from report_snapshots where company_id=? and account_id=? and id=?""",
                                bad,
                                c.companyId,
                                c.accountId,
                                c.id,
                            )
                            sql.execute(
                                "update report_snapshots set row_count=0,stored_bytes=0 where id=?",
                                bad,
                            )
                        }
                    } as Result.Failed)
                    .failure
                    .code,
            )
        }
    }
}
