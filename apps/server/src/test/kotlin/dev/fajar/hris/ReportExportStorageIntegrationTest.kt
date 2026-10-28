package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.*
import dev.fajar.hris.storage.domain.entities.ObjectCleanupRequest
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/** Persistence and fencing acceptance; provider I/O and public admission are separate workflows. */
class ReportExportStorageIntegrationTest : ApiIntegrationTest() {
    @Autowired private lateinit var exports: ReportExportRepository
    @Autowired private lateinit var snapshots: ReportSnapshotRepository
    @Autowired private lateinit var cleanup: ObjectCleanupRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var sql: DSLContext

    private data class Fixture(val actor: Actor, val request: ReportExportRequest)

    private fun <T> value(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    private fun fixture(expired: Boolean = false): Fixture {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,'Export storage','UTC')",
                company,
                "E${company.toString().take(8)}",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Export owner')",
                account,
                "$account@example.test",
            )
        val at =
            Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(if (expired) 14400 else 60)
        val actor = Actor(account, company, emptySet(), at, UUID.randomUUID())
        val request =
            value(
                validateReportExportRequest(
                    ReportExportRequest(
                        UUID.randomUUID(),
                        company,
                        account,
                        ReportSelection(
                            ReportKind.HEADCOUNT,
                            "headcount.v1",
                            ReportFilterParameters(asOf = "2026-10-01"),
                        ),
                        ReportExportDataset.SUMMARY,
                        mapOf(company to setOf("reports.read", "people.read")),
                        ClientRequest(null, false),
                        at,
                        at.plusSeconds(3600),
                    )
                )
            )
        return Fixture(actor, request)
    }

    private fun job(f: Fixture, id: UUID = UUID.randomUUID()): UUID {
        database()
            .update(
                """insert into background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,total_items)
            values(?,?,?,'REPORT_EXPORT',?,?::jsonb,?::timestamptz,0,?,100002)""",
                id,
                f.request.companyId,
                f.actor.accountId,
                UUID.randomUUID(),
                json.writeValueAsString(mapOf("exportId" to f.request.id.toString())),
                f.request.createdAt.toString(),
                UUID.randomUUID(),
            )
        return id
    }

    private fun <T> run(f: Fixture, op: () -> Result<T>) =
        transactions.run(f.actor) {
            exports.lockOwner(f.request.companyId, f.actor.accountId).flatMap { op() }
        }

    private fun find(f: Fixture) =
        value(run(f) { exports.find(f.request.companyId, f.actor.accountId, f.request.id, true) })!!

    private fun create(f: Fixture): ReportExport {
        val job = job(f)
        assertEquals(Result.Success(true), run(f) { exports.create(f.request, job) })
        return find(f)
    }

    private fun storeSnapshot(f: Fixture, attach: Boolean = true): ReportSnapshot {
        val r = f.request
        val c =
            ReportSnapshotCapture(
                r.id,
                r.companyId,
                r.accountId,
                r.selection,
                r.dataset,
                value(reportExportColumns(r.selection.kind, r.dataset)),
                r.createdAt.plusSeconds(1),
                r.expiresAt,
            )
        val counts = HeadcountCounts(1, 1, 1, 0, 0, 1, 0)
        val rows =
            value(
                reportSummaryRows(
                    c,
                    GroupHeadcountCounts(counts, listOf(CompanyHeadcount(r.companyId, counts))),
                )
            )
        val current = find(f)
        return value(
            run(f) {
                snapshots
                    .begin(c)
                    .flatMap { snapshots.append(c, 0, rows) }
                    .flatMap { snapshots.seal(c) }
                    .flatMap { snapshot ->
                        if (attach)
                            exports.captured(current, c.evaluatedAt).map {
                                assertTrue(it)
                                snapshot
                            }
                        else Result.Success(snapshot)
                    }
            }
        )
    }

    private fun upload(f: Fixture, current: ReportExport, rows: Int = 1) =
        ReportExportUpload(
            UUID.randomUUID(),
            f.request.companyId,
            f.actor.accountId,
            f.request.id,
            current.partCount + 1,
            current.completedRows,
            rows,
            current.byteCount,
            100,
            "a".repeat(64),
            current.updatedAt.plusSeconds(1),
        )

    private fun schedule(f: Fixture, p: ReportExportUpload) =
        cleanup.schedule(
            ObjectCleanupRequest(
                p.id,
                p.companyId,
                p.exportId,
                reportExportObjectKey(p),
                p.bytes.toLong(),
                p.accountId,
                f.request.expiresAt.plusSeconds(3600),
            )
        )

    private fun reserve(f: Fixture, current: ReportExport, p: ReportExportUpload) =
        run(f) {
            schedule(f, p)
                .flatMap { exports.reserve(current, p) }
                .flatMap { changed ->
                    if (changed) Result.Success(Unit)
                    else Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                }
        }

    private fun accept(f: Fixture, p: ReportExportUpload): ReportExport {
        val current = find(f)
        assertEquals(
            Result.Success(true),
            run(f) {
                exports.accept(
                    current,
                    ReportExportPart(p, "provider-etag"),
                    current.updatedAt.plusSeconds(1),
                )
            },
        )
        return find(f)
    }

    private fun stopJob(id: UUID) {
        database()
            .update(
                "update background_jobs set status='FAILED',finished_at=now(),version=version+1 where id=?",
                id,
            )
    }

    @Test
    fun immutablePartProgressAndSnapshotSurviveExplicitRecoveryBeforePublication() {
        val f = fixture()
        create(f)
        val snapshot = storeSnapshot(f)
        var current = find(f)
        val first = upload(f, current)
        assertEquals(Result.Success(Unit), reserve(f, current, first))
        current = accept(f, first)
        assertEquals(1, current.completedRows)
        assertEquals(1, current.partCount)
        assertEquals(100, current.byteCount)
        assertInstanceOf(
            Result.Failed::class.java,
            run(f) { exports.publish(current, "a".repeat(64), current.updatedAt.plusSeconds(1)) },
        )
        assertEquals(current, find(f))
        assertEquals(
            Result.Success(true),
            run(f) {
                exports.stop(current, "provider_unavailable", current.updatedAt.plusSeconds(1))
            },
        )
        val stopped = find(f)
        val nextJob = job(f)
        assertInstanceOf(
            Result.Failed::class.java,
            run(f) { exports.resume(stopped, nextJob, stopped.updatedAt.plusSeconds(1)) },
        )
        stopJob(stopped.jobId)
        assertEquals(
            Result.Success(true),
            run(f) { exports.resume(stopped, nextJob, stopped.updatedAt.plusSeconds(1)) },
        )
        current = find(f)
        assertEquals(2, current.attempt)
        assertEquals(snapshot.capture.id, current.snapshotId)
        assertEquals(f.request.expiresAt, current.request.expiresAt)
        assertEquals(1, current.completedRows)
        val second = upload(f, current)
        assertEquals(Result.Success(Unit), reserve(f, current, second))
        current = accept(f, second)
        val parts =
            value(run(f) { exports.parts(f.request.companyId, f.actor.accountId, f.request.id) })
        assertEquals(listOf(first.id, second.id), parts.map { it.upload.id })
        val manifest = value(reportExportManifest(snapshot, parts))
        assertEquals(
            Result.Success(true),
            run(f) { exports.publish(current, manifest.tag, current.updatedAt.plusSeconds(1)) },
        )
        val ready = find(f)
        assertEquals(ReportExportStatus.READY, ready.status)
        assertEquals(2, ready.completedRows)
        assertEquals(manifest.tag, ready.manifestTag)
        for (statement in
            listOf(
                "update report_exports set expires_at=expires_at+interval '1 second',version=version+1 where id=?",
                "update report_exports set sources='{}'::jsonb,version=version+1 where id=?",
                "update report_exports set columns='[]'::jsonb,version=version+1 where id=?",
            )) assertInstanceOf(
            Result.Failed::class.java,
            run(f) { safeDatabaseCall { sql.execute(statement, f.request.id) } },
        )
        assertEquals(200, ready.byteCount)
        for (statement in
            listOf(
                "update report_export_parts set etag='changed' where export_id=?",
                "delete from report_export_parts where export_id=?",
                "update report_export_uploads set sha256=repeat('b',64) where export_id=?",
                "delete from report_export_uploads where export_id=?",
            )) assertInstanceOf(
            Result.Failed::class.java,
            run(f) { safeDatabaseCall { sql.execute(statement, f.request.id) } },
        )
        assertEquals(
            Result.Success(false),
            run(f) { exports.stop(ready, "cancelled", ready.updatedAt.plusSeconds(1)) },
        )
        assertEquals(
            2,
            value(run(f) { exports.parts(f.request.companyId, f.actor.accountId, f.request.id) })
                .size,
        )
    }

    @Test
    fun cleanupReservationAndExactCapturedDefinitionAreRequiredBeforePartsCanAdvance() {
        val f = fixture()
        val queued = create(f)
        assertInstanceOf(
            Result.Failed::class.java,
            run(f) { exports.captured(queued, queued.updatedAt.plusSeconds(1)) },
        )
        val wrong =
            f.copy(
                request =
                    f.request.copy(
                        selection =
                            f.request.selection.copy(
                                parameters =
                                    f.request.selection.parameters.copy(asOf = "2026-10-02")
                            )
                    )
            )
        storeSnapshot(wrong, attach = false)
        assertInstanceOf(
            Result.Failed::class.java,
            run(f) { exports.captured(queued, queued.updatedAt.plusSeconds(2)) },
        )
        assertEquals(ReportExportStatus.QUEUED, find(f).status)
        val good = fixture()
        create(good)
        storeSnapshot(good)
        val current = find(good)
        val p = upload(good, current)
        assertInstanceOf(Result.Failed::class.java, run(good) { exports.reserve(current, p) })
        assertEquals(0, find(good).reservationCount)
        assertEquals(Result.Success(Unit), reserve(good, current, p))
        val reserved = find(good)
        assertEquals(
            Result.Success(false),
            run(good) {
                exports.accept(
                    reserved,
                    ReportExportPart(p.copy(sha256 = "b".repeat(64)), "etag"),
                    p.createdAt.plusSeconds(1),
                )
            },
        )
        assertEquals(0, find(good).partCount)
        assertInstanceOf(
            Result.Failed::class.java,
            run(good) {
                exports.publish(reserved, "a".repeat(64), reserved.updatedAt.plusSeconds(1))
            },
        )
    }

    @Test
    fun competingReservationsAndLateAcknowledgementsCannotOverwriteProgress() {
        val f = fixture()
        create(f)
        storeSnapshot(f)
        val current = find(f)
        val uploads = List(2) { upload(f, current) }
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures =
                uploads.map { p ->
                    pool.submit<Result<Boolean>> {
                        transactions.run(f.actor) {
                            schedule(f, p).flatMap {
                                ready.countDown()
                                assertTrue(start.await(5, TimeUnit.SECONDS))
                                exports.reserve(current, p).flatMap { changed ->
                                    if (changed) Result.Success(true)
                                    else
                                        Result.Failed(
                                            Failure(FailureKind.CONFLICT, "stale_version")
                                        )
                                }
                            }
                        }
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, outcomes.count { it is Result.Success })
            assertEquals(1, outcomes.count { it is Result.Failed })
            val winner = uploads[outcomes.indexOfFirst { it is Result.Success }]
            val reserved = find(f)
            assertEquals(1, reserved.reservationCount)
            val later = upload(f, reserved)
            assertEquals(Result.Success(Unit), reserve(f, reserved, later))
            assertEquals(
                Result.Success(false),
                run(f) {
                    exports.accept(
                        reserved,
                        ReportExportPart(winner, "late"),
                        later.createdAt.plusSeconds(1),
                    )
                },
            )
            accept(f, later)
            assertEquals(
                listOf(later.id),
                value(
                        run(f) {
                            exports.parts(f.request.companyId, f.actor.accountId, f.request.id)
                        }
                    )
                    .map { it.upload.id },
            )
            assertEquals(2, find(f).reservationCount)
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun ownerIsolationAndDatabaseQuotasBoundActiveAndUnretiredRequests() {
        val f = fixture()
        val first = create(f)
        val second = f.copy(request = f.request.copy(id = UUID.randomUUID()))
        create(second)
        val third = f.copy(request = f.request.copy(id = UUID.randomUUID()))
        val thirdJob = job(third)
        assertEquals(Result.Success(false), run(f) { exports.create(f.request, first.jobId) })
        assertInstanceOf(
            Result.Failed::class.java,
            run(third) { exports.create(third.request, thirdJob) },
        )
        assertEquals(
            Result.Success(ReportExportQuota(2, 2)),
            run(f) { exports.quota(f.request.companyId, f.actor.accountId) },
        )
        val other = fixture()
        assertEquals(
            Result.Success(null),
            transactions.run(other.actor) {
                exports.find(f.request.companyId, f.actor.accountId, f.request.id)
            },
        )
        assertTrue(
            value(
                    transactions.run(other.actor) {
                        exports.list(f.request.companyId, f.actor.accountId, null, 51)
                    }
                )
                .isEmpty()
        )
        val sameCompany = other.actor.copy(companyId = f.actor.companyId)
        assertEquals(
            Result.Success(null),
            transactions.run(sameCompany) {
                exports.find(f.request.companyId, f.actor.accountId, f.request.id)
            },
        )
        for (entry in listOf(first, find(second))) assertEquals(
            Result.Success(true),
            run(f) { exports.stop(entry, "test_stopped", entry.updatedAt.plusSeconds(1)) },
        )
        repeat(8) {
            val next = f.copy(request = f.request.copy(id = UUID.randomUUID()))
            val entry = create(next)
            assertEquals(
                Result.Success(true),
                run(next) { exports.stop(entry, "test_stopped", entry.updatedAt.plusSeconds(1)) },
            )
        }
        assertEquals(
            Result.Success(ReportExportQuota(0, 10)),
            run(f) { exports.quota(f.request.companyId, f.actor.accountId) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            run(third) { exports.create(third.request, thirdJob) },
        )
        val page = value(run(f) { exports.list(f.request.companyId, f.actor.accountId, null, 4) })
        val last = page.last().request
        val next =
            value(
                run(f) {
                    exports.list(
                        f.request.companyId,
                        f.actor.accountId,
                        ReportExportCursor(last.createdAt, last.id),
                        51,
                    )
                }
            )
        assertEquals(10, (page + next).map { it.request.id }.toSet().size)
        val stopped = find(f)
        assertEquals(
            Result.Success(false),
            run(f) { exports.retire(stopped, f.request.expiresAt.plusSeconds(3601)) },
        )
    }

    @Test
    fun expiryRetiresOnlyAfterOwnedRowsAndCleanupEvidenceAreGoneAndKeepsOriginalMetadata() {
        val f = fixture(expired = true)
        val queued = create(f)
        val snapshot = storeSnapshot(f, attach = false)
        assertEquals(
            Result.Success(true),
            run(f) {
                exports.stop(queued, "report_export_expired", queued.updatedAt.plusSeconds(1))
            },
        )
        val stopped = find(f)
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        assertEquals(Result.Success(false), run(f) { exports.retire(stopped, now) })
        assertEquals(
            Result.Success(2),
            run(f) {
                snapshots.deleteExpiredRows(
                    f.request.companyId,
                    f.actor.accountId,
                    f.request.id,
                    now,
                    500,
                )
            },
        )
        val cleanupId = UUID.randomUUID()
        assertEquals(
            Result.Success(Unit),
            run(f) {
                cleanup.schedule(
                    ObjectCleanupRequest(
                        cleanupId,
                        f.request.companyId,
                        f.request.id,
                        "${f.request.companyId}/reports/${f.request.id}/$cleanupId",
                        100,
                        f.actor.accountId,
                        f.request.expiresAt,
                    )
                )
            },
        )
        assertEquals(Result.Success(false), run(f) { exports.retire(stopped, now) })
        // Represents completion of the cleanup queue's separately tested provider workflow.
        database().update("delete from object_cleanup_queue where id=?", cleanupId)
        assertEquals(Result.Success(true), run(f) { exports.retire(stopped, now) })
        val retired = find(f)
        assertEquals(stopped.request, retired.request)
        assertEquals(now, retired.retiredAt)
        assertEquals(
            Result.Success(ReportExportQuota(0, 0)),
            run(f) { exports.quota(f.request.companyId, f.actor.accountId) },
        )
        assertEquals(
            Result.Success(snapshot),
            run(f) { snapshots.find(f.request.companyId, f.actor.accountId, f.request.id) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            run(f) {
                safeDatabaseCall {
                    sql.execute("delete from report_exports where id=?", f.request.id)
                }
            },
        )
    }

    @Test
    fun rawReservationLimitsRejectTheNextAttemptAtomicallyWithoutLeakingCleanupRows() {
        for ((count, bytes) in listOf(1088 to 100, 256 to 1048576)) {
            val f = fixture()
            create(f)
            storeSnapshot(f)
            val initial = find(f)
            fun batch(amount: Int) =
                run(f) {
                    safeDatabaseCall {
                        val ids = List(amount) { UUID.randomUUID() }
                        sql.execute(
                            """insert into object_cleanup_queue(id,company_id,resource_id,object_key,object_bytes,created_by,eligible_at)
                    select x,?,?,?::text||'/reports/'||?::text||'/'||x::text,?,?,?::timestamptz from unnest(?::uuid[]) x""",
                            f.request.companyId,
                            f.request.id,
                            f.request.companyId.toString(),
                            f.request.id.toString(),
                            bytes,
                            f.actor.accountId,
                            f.request.expiresAt.plusSeconds(3600).toString(),
                            ids.toTypedArray(),
                        )
                        sql.execute(
                            """insert into report_export_uploads(company_id,account_id,export_id,id,part_number,after_row,row_count,byte_offset,byte_count,sha256,created_at)
                    select ?,?,?,x,1,0,1,0,?,repeat('a',64),?::timestamptz from unnest(?::uuid[]) x""",
                            f.request.companyId,
                            f.actor.accountId,
                            f.request.id,
                            bytes,
                            initial.updatedAt.plusSeconds(1).toString(),
                            ids.toTypedArray(),
                        )
                    }
                }
            assertEquals(Result.Success(count), batch(count))
            val full = find(f)
            assertEquals(count, full.reservationCount)
            assertEquals(count.toLong() * bytes, full.reservedBytes)
            assertInstanceOf(Result.Failed::class.java, batch(1))
            assertEquals(full, find(f))
            assertEquals(
                count,
                database()
                    .queryForObject(
                        "select count(*) from object_cleanup_queue where company_id=? and resource_id=?",
                        Int::class.java,
                        f.request.companyId,
                        f.request.id,
                    ),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run(f) {
                    safeDatabaseCall {
                        sql.execute(
                            "update report_exports set completed_rows=1,part_count=1,byte_count=100,version=version+1 where id=?",
                            f.request.id,
                        )
                    }
                },
            )
        }
    }
}
