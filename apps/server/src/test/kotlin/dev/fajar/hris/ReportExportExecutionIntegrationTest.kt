package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.*
import dev.fajar.hris.reporting.domain.usecases.*
import dev.fajar.hris.storage.domain.entities.*
import dev.fajar.hris.storage.domain.repositories.*
import java.security.MessageDigest
import java.time.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Actual admission, snapshot, lease and cleanup transactions; the private provider is controlled.
 */
class ReportExportExecutionIntegrationTest : GroupHeadcountApiFixture() {
    @Autowired private lateinit var exports: ReportExportRepository
    @Autowired private lateinit var snapshots: ReportSnapshotRepository
    @Autowired private lateinit var source: ReportSnapshotSourceRepository
    @Autowired private lateinit var headcount: HeadcountReportRepository
    @Autowired private lateinit var attendance: AttendanceExceptionReportRepository
    @Autowired private lateinit var leave: LeaveReportRepository
    @Autowired private lateinit var expenses: ExpenseReportRepository
    @Autowired private lateinit var overtime: OvertimeReportRepository
    @Autowired private lateinit var payroll: PayrollCostReportRepository
    @Autowired private lateinit var closings: AttendanceClosingReportRepository
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var policies: CompanyClientPolicyRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var cleanup: ObjectCleanupRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var sources: ReportSourceTransactionRunner
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var security: IdentitySecurityPolicy
    @Autowired private lateinit var manage: ManageReportExports
    @Autowired private lateinit var abort: AbortReportExport
    @Autowired private lateinit var defaultStorage: ObjectStorageRepository

    @org.junit.jupiter.api.BeforeEach
    fun alignExportDeadlineWithDatabase() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
    }

    private val browserClient = ClientRequest(null, false)

    private fun <T> value(result: Result<T>): T {
        assertTrue(result is Result.Success, "$result")
        return (result as Result.Success).value
    }

    private fun rejected(result: Result<*>, code: String? = null) {
        assertTrue(result is Result.Failed, "$result")
        if (code != null) assertEquals(code, (result as Result.Failed).failure.code)
    }

    private fun selection(kind: ReportKind = ReportKind.HEADCOUNT) =
        ReportSelection(
            kind,
            kind.definitionVersion,
            when (kind) {
                ReportKind.HEADCOUNT -> ReportFilterParameters(asOf = "2026-10-01")
                ReportKind.PAYROLL_COSTS,
                ReportKind.ATTENDANCE_CLOSINGS ->
                    ReportFilterParameters(from = "2026-10", until = "2026-10")
                else -> ReportFilterParameters(from = "2026-10-01", until = "2026-10-03")
            },
        )

    private fun actor(f: Fixture) =
        f.actor.copy(
            permissions = ReportKind.entries.flatMap { reportExportSourcePermissions(it) }.toSet()
        )

    private fun start(
        f: Fixture,
        input: ReportSelection = selection(),
        dataset: ReportExportDataset = ReportExportDataset.SUMMARY,
    ) = value(manage.start(actor(f), UUID.randomUUID(), input, dataset, browserClient)).id

    private fun find(f: Fixture, id: UUID) =
        value(transactions.run(actor(f)) { exports.find(f.company, f.account, id) })!!

    private fun parts(f: Fixture, id: UUID) =
        value(transactions.run(actor(f)) { exports.parts(f.company, f.account, id) })

    private fun lease(f: Fixture, id: UUID): JobLease {
        val export = find(f, id)
        val owner = UUID.randomUUID()
        val token = UUID.randomUUID()
        assertEquals(
            1,
            database()
                .update(
                    """update background_jobs set status='RUNNING',lease_owner=?,lease_token=?,lease_until=clock_timestamp()+interval '5 minutes',attempts=attempts+1,version=version+1 where id=? and status='QUEUED'""",
                    owner,
                    token,
                    export.jobId,
                ),
        )
        val job = value(transactions.run(actor(f)) { jobs.find(f.company, export.jobId) })!!
        return JobLease(job, owner, token, Instant.now().plusSeconds(300))
    }

    private fun worker(
        storage: ObjectStorageRepository = defaultStorage,
        details: ReportSnapshotSourceRepository = source,
        rows: ReportSnapshotRepository = snapshots,
        events: ChangeJournalRepository = journal,
        time: Clock = clock,
    ) =
        AdvanceReportExport(
            exports,
            rows,
            details,
            headcount,
            attendance,
            leave,
            expenses,
            overtime,
            payroll,
            closings,
            jobs,
            policies,
            companies,
            members,
            identities,
            storage,
            cleanup,
            events,
            transactions,
            sources,
            time,
        )

    private fun commands(
        repository: ReportExportRepository = exports,
        events: ChangeJournalRepository = journal,
        time: Clock = clock,
        assurance: IdentitySecurityPolicy = security,
    ) =
        ManageReportExports(
            repository,
            jobs,
            policies,
            companies,
            members,
            identities,
            operations,
            events,
            transactions,
            sources,
            assurance,
            time,
        )

    private inner class Provider : ObjectStorageRepository by defaultStorage {
        val objects = linkedMapOf<String, ByteArray>()
        var afterPut: () -> Unit = {}
        var response: (StoredObject) -> Result<StoredObject> = { Result.Success(it) }

        override fun put(key: String, bytes: ByteArray): Result<StoredObject> {
            assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive(),
                "Provider held a SQL transaction",
            )
            assertFalse(objects.containsKey(key), "Attempt key was reused")
            val ids = key.split('/')
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from object_cleanup_queue where id=? and resource_id=? and object_key=? and status='PENDING'",
                        Int::class.java,
                        UUID.fromString(ids.last()),
                        UUID.fromString(ids[2]),
                        key,
                    ),
            )
            objects[key] = bytes.copyOf()
            afterPut()
            return response(
                StoredObject(key, bytes.size.toLong(), "etag-${objects.size}", digest(bytes))
            )
        }
    }

    private fun digest(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun finish(
        f: Fixture,
        id: UUID,
        task: AdvanceReportExport,
        lease: JobLease,
    ): ReportExport {
        var last = lease.job.completedItems
        for (step in 1..REPORT_EXPORT_JOB_STEPS) {
            val progress = value(task.execute(actor(f), lease))
            assertTrue(progress.completedItems > last)
            last = progress.completedItems
            if (progress.finished) {
                val current = find(f, id)
                assertEquals(ReportExportStatus.READY, current.status)
                assertEquals(
                    JobStatus.SUCCEEDED,
                    value(transactions.run(actor(f)) { jobs.find(f.company, current.jobId) })!!
                        .status,
                )
                return current
            }
        }
        fail<Unit>("Export did not terminate")
        error("unreachable")
    }

    private fun revoke(f: Fixture, permission: String) {
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                f.company,
                f.account,
                permission,
            )
    }

    private fun exportFixture(): Fixture {
        val f = fixture()
        for (permission in
            ReportKind.entries.flatMap { reportExportSourcePermissions(it) }.toSet()) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?) on conflict do nothing",
                f.company,
                f.account,
                permission,
            )
        return f
    }

    @Test
    fun everySummaryAndSupportedEmptyDetailPublishesWithFinitePositiveProgress() {
        val provider = Provider()
        val task = worker(provider)
        for (kind in ReportKind.entries) {
            val f = exportFixture()
            val id = start(f, selection(kind))
            val current = finish(f, id, task, lease(f, id))
            assertTrue(current.completedRows > 0)
            assertEquals(1, current.partCount)
            val p = parts(f, id).single()
            val bytes = provider.objects.getValue(reportExportObjectKey(p.upload))
            assertEquals(current.byteCount, bytes.size.toLong())
            assertEquals(p.upload.sha256, digest(bytes))
            assertTrue(bytes.toString(Charsets.UTF_8).startsWith("\uFEFF\"definition_version\""))
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from object_cleanup_queue where resource_id=?",
                        Int::class.java,
                        id,
                    ),
            )
        }
        for (kind in
            listOf(ReportKind.LEAVE, ReportKind.EXPENSES, ReportKind.ATTENDANCE_CLOSINGS)) {
            val f = exportFixture()
            val id = start(f, selection(kind), ReportExportDataset.DETAIL)
            val current = finish(f, id, task, lease(f, id))
            assertEquals(0, current.completedRows)
            assertEquals(1, current.partCount)
            assertEquals(
                1,
                provider.objects
                    .getValue(reportExportObjectKey(parts(f, id).single().upload))
                    .toString(Charsets.UTF_8)
                    .split("\r\n")
                    .size - 1,
            )
        }
    }

    @Test
    fun lostResponseReplaysSameServerIdentityAtQuotaAndConcurrentAdmissionDoesNotOverfill() {
        val f = fixture()
        val key = UUID.randomUUID()
        val first =
            value(
                manage.start(actor(f), key, selection(), ReportExportDataset.SUMMARY, browserClient)
            )
        assertNotEquals(key, first.id)
        start(f)
        val replay =
            value(
                manage.start(actor(f), key, selection(), ReportExportDataset.SUMMARY, browserClient)
            )
        assertEquals(first.copy(replayed = true), replay)
        rejected(
            manage.start(
                actor(f),
                UUID.randomUUID(),
                selection(),
                ReportExportDataset.SUMMARY,
                browserClient,
            ),
            "report_export_active_limit",
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from background_jobs where company_id=? and kind='REPORT_EXPORT'",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=?",
                    Int::class.java,
                    first.id,
                ),
        )
        val g = fixture()
        val ready = CountDownLatch(3)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(3).use { pool ->
            val pending =
                (1..3).map {
                    pool.submit<Result<MutationReceipt>> {
                        ready.countDown()
                        assertTrue(go.await(5, TimeUnit.SECONDS))
                        manage.start(
                            actor(g),
                            UUID.randomUUID(),
                            selection(),
                            ReportExportDataset.SUMMARY,
                            browserClient,
                        )
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            val results = pending.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(2, results.count { it is Result.Success })
            assertEquals(
                1,
                results.count {
                    it is Result.Failed && it.failure.code == "report_export_active_limit"
                },
            )
        }
    }

    @Test
    fun sourceScopeExcludesAnchorFromFactsButRequiresItsAuthorityAndRechecksAfterWait() {
        val f = fixture()
        val g = anotherCompany(f)
        create(f)
        val input =
            selection()
                .copy(
                    parameters =
                        ReportFilterParameters(asOf = "2026-10-01", companies = listOf(g.company))
                )
        val id = start(f, input)
        val task = worker(Provider())
        val l = lease(f, id)
        assertEquals(JobStep(1, false), value(task.execute(actor(f), l)))
        val snapshot =
            value(transactions.run(actor(f)) { snapshots.find(f.company, f.account, id) })!!
        assertEquals(listOf(g.company), snapshot.capture.selection.parameters.companies)
        val rows = value(transactions.run(actor(f)) { snapshots.rows(snapshot, 0, 500) })
        assertEquals(2, rows.size)
        assertEquals(ReportExportCell.Integer(0), rows.first().cells[5])
        revoke(g, "people.read")
        rejected(task.execute(actor(f), l))
        value(abort.execute(l, Failure(FailureKind.FORBIDDEN, "report_permission_denied")))
        assertEquals(ReportExportStatus.STOPPED, find(f, id).status)
        val h = fixture()
        var called = false
        val gate =
            object : ReportExportRepository by exports {
                override fun lockOwner(company: UUID, account: UUID): Result<Unit> {
                    if (account == h.account && !called) {
                        called = true
                        revoke(h, "people.read")
                    }
                    return exports.lockOwner(company, account)
                }
            }
        rejected(
            commands(gate)
                .start(
                    actor(h),
                    UUID.randomUUID(),
                    selection(),
                    ReportExportDataset.SUMMARY,
                    browserClient,
                )
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from report_exports where company_id=?",
                    Int::class.java,
                    h.company,
                ),
        )
    }

    @Test
    fun cancellationDuringProviderIoRetainsOrphanCleanupAndResumeReusesSnapshot() {
        val f = fixture()
        val id = start(f)
        val l = lease(f, id)
        val provider = Provider()
        val task = worker(provider)
        value(task.execute(actor(f), l))
        val original =
            value(transactions.run(actor(f)) { snapshots.find(f.company, f.account, id) })!!
        val cancelKey = UUID.randomUUID()
        var version = 0L
        provider.afterPut = {
            version = find(f, id).version
            value(manage.cancel(actor(f), cancelKey, id, version))
        }
        rejected(task.execute(actor(f), l), "job_cancellation_requested")
        var current = find(f, id)
        assertEquals(0, current.partCount)
        assertEquals(1, current.reservationCount)
        assertEquals(ReportExportStatus.STOPPED, current.status)
        assertTrue(value(manage.cancel(actor(f), cancelKey, id, version)).replayed)
        value(abort.execute(l, Failure(FailureKind.CONFLICT, "job_cancellation_requested")))
        val resumeKey = UUID.randomUUID()
        val oldVersion = find(f, id).version
        value(manage.resume(actor(f), resumeKey, id, oldVersion, browserClient))
        assertTrue(
            value(manage.resume(actor(f), resumeKey, id, oldVersion, browserClient)).replayed
        )
        create(f)
        provider.afterPut = {}
        current = finish(f, id, task, lease(f, id))
        assertEquals(2, current.attempt)
        assertEquals(2, current.reservationCount)
        assertEquals(1, current.partCount)
        assertEquals(
            original,
            value(transactions.run(actor(f)) { snapshots.find(f.company, f.account, id) }),
        )
        assertEquals(2, provider.objects.size)
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from object_cleanup_queue where resource_id=?",
                    Int::class.java,
                    id,
                ),
        )
    }

    @Test
    fun providerResponsesAndPostUploadRevocationExpiryAndLeaseLossCannotPublishParts() {
        for (mode in
            listOf(
                "key",
                "size",
                "hash",
                "etag",
                "permission",
                "credential",
                "lease",
                "expiry",
                "policy",
            )) {
            val f = fixture()
            val id = start(f)
            val l = lease(f, id)
            val provider = Provider()
            var at = clock.instant()
            val time =
                object : Clock() {
                    override fun getZone() = ZoneOffset.UTC

                    override fun withZone(zone: ZoneId) = this

                    override fun instant() = at
                }
            val task = worker(provider, time = time)
            value(task.execute(actor(f), l))
            provider.response = { stored ->
                Result.Success(
                    when (mode) {
                        "key" -> stored.copy(key = stored.key + "wrong")
                        "size" -> stored.copy(size = stored.size + 1)
                        "hash" -> stored.copy(sha256 = "0".repeat(64))
                        "etag" -> stored.copy(etag = "x".repeat(201))
                        else -> stored
                    }
                )
            }
            provider.afterPut = {
                when (mode) {
                    "permission" -> revoke(f, "people.read")
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    "lease" ->
                        database()
                            .update(
                                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                                l.job.request.id,
                            )
                    "expiry" -> at = find(f, id).request.expiresAt
                    "policy" ->
                        assertEquals(
                            200,
                            savePolicy(f, changes = mapOf("disabledModules" to listOf("REPORTING")))
                                .statusCode(),
                        )
                    else -> Unit
                }
            }
            rejected(task.execute(actor(f), l))
            val current = find(f, id)
            assertEquals(0, current.partCount, mode)
            assertEquals(1, current.reservationCount, mode)
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from object_cleanup_queue where resource_id=?",
                        Int::class.java,
                        id,
                    ),
            )
            value(abort.execute(l, Failure(FailureKind.UNAVAILABLE, "provider_failed")))
            if (mode != "lease") assertEquals(ReportExportStatus.STOPPED, find(f, id).status)
        }
    }

    @Test
    fun byteLimitedPartsResumeWithoutSkippingRowsOrRepeatingAcceptedUploads() {
        val f = fixture()
        val id = start(f, selection(ReportKind.LEAVE), ReportExportDataset.DETAIL)
        var captures = 0
        // Synthetic wide facts isolate renderer progress; source SQL has independent acceptance.
        val generated =
            object : ReportSnapshotSourceRepository {
                override fun captureDetails(
                    capture: ReportSnapshotCapture
                ): Result<ReportCaptureTotals> {
                    captures++
                    for (first in 0 until 1201 step 30) {
                        val batch =
                            (first until minOf(first + 30, 1201)).map { row ->
                                capture.columns.map { column ->
                                    when (column.type) {
                                        ReportExportColumnType.TEXT ->
                                            ReportExportCell.Text(
                                                if (column.key == "employee_name") "\"".repeat(4000)
                                                else "row-$row"
                                            )
                                        ReportExportColumnType.INTEGER ->
                                            ReportExportCell.Integer(row.toLong())
                                        ReportExportColumnType.DECIMAL ->
                                            ReportExportCell.Decimal(java.math.BigDecimal("0.5"))
                                    }
                                }
                            }
                        val appended = snapshots.append(capture, first, batch)
                        if (appended is Result.Failed) return appended
                    }
                    return Result.Success(ReportCaptureTotals(1201, 0))
                }
            }
        var fetched = 0
        // Exercise the encoder's consumed prefix even if a repository returns wider bounded pages.
        val wider =
            object : ReportSnapshotRepository by snapshots {
                override fun rows(
                    snapshot: ReportSnapshot,
                    after: Int,
                    limit: Int,
                ): Result<List<ReportSnapshotRow>> =
                    snapshots.rows(snapshot, after, limit / 2).flatMap { first ->
                        if (first.isEmpty()) Result.Success(first)
                        else
                            snapshots.rows(snapshot, after + first.size, limit - first.size).map {
                                second ->
                                (first + second).also { fetched = it.size }
                            }
                    }
            }
        val provider = Provider()
        val task = worker(provider, generated, wider)
        val l = lease(f, id)
        value(task.execute(actor(f), l))
        value(task.execute(actor(f), l))
        val accepted = parts(f, id).single()
        assertTrue(accepted.upload.rows < fetched)
        provider.response = {
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "provider_lost_response"))
        }
        rejected(task.execute(actor(f), l), "provider_lost_response")
        value(abort.execute(l, Failure(FailureKind.UNAVAILABLE, "provider_lost_response")))
        val stopped = find(f, id)
        assertEquals(accepted.upload.rows, stopped.completedRows)
        value(manage.resume(actor(f), UUID.randomUUID(), id, stopped.version, browserClient))
        provider.response = { Result.Success(it) }
        val done = finish(f, id, task, lease(f, id))
        assertEquals(1, captures)
        assertEquals(1201, done.completedRows)
        val all = parts(f, id)
        assertEquals(accepted, all.first())
        assertEquals(done.partCount + 1, done.reservationCount)
        assertEquals(1201, all.sumOf { it.upload.rows })
        val bytes = java.io.ByteArrayOutputStream()
        all.forEach { part ->
            assertTrue(part.upload.bytes <= REPORT_CSV_MAX_PART_BYTES)
            bytes.write(provider.objects.getValue(reportExportObjectKey(part.upload)))
        }
        assertEquals(done.byteCount, bytes.size().toLong())
        val lines = bytes.toString(Charsets.UTF_8).split("\r\n")
        assertEquals(1203, lines.size)
        for (row in 0 until 1201) assertTrue(
            lines[row + 1].startsWith("\"row-$row\","),
            "Skipped or repeated row $row",
        )
    }

    @Test
    fun interactiveAssuranceExpiryDuringJournalRollsBackStartCancelAndResume() {
        for (action in listOf("start", "cancel", "resume")) {
            clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
            val f = exportFixture()
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    f.account,
                )
            val at = clock.instant()
            val secured = actor(f).copy(mfaVerifiedAt = at)
            val events =
                object : ChangeJournalRepository by journal {
                    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                        journal.record(actor, change).map { clock.set(at.plusSeconds(12 * 3600)) }
                }
            val strict = commands(events = events, assurance = security.copy(enforceMfa = true))
            if (action == "start") {
                rejected(
                    strict.start(
                        secured,
                        UUID.randomUUID(),
                        selection(),
                        ReportExportDataset.SUMMARY,
                        browserClient,
                    ),
                    "mfa_required",
                )
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from background_jobs where company_id=?",
                            Int::class.java,
                            f.company,
                        ),
                )
            } else {
                val id = start(f)
                if (action == "resume")
                    value(
                        abort.execute(
                            lease(f, id),
                            Failure(FailureKind.UNAVAILABLE, "test_stopped"),
                        )
                    )
                val before = find(f, id)
                val result =
                    if (action == "cancel")
                        strict.cancel(secured, UUID.randomUUID(), id, before.version)
                    else
                        strict.resume(secured, UUID.randomUUID(), id, before.version, browserClient)
                rejected(result, "mfa_required")
                assertEquals(before, find(f, id))
                assertEquals(
                    1,
                    database()
                        .queryForObject(
                            "select count(*) from background_jobs where company_id=?",
                            Int::class.java,
                            f.company,
                        ),
                )
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select cancellation_requested from background_jobs where id=?",
                            Boolean::class.java,
                            before.jobId,
                        ),
                )
            }
        }
    }

    @Test
    fun journalFailureRollsBackAdmissionAndCaptureTogetherWithJobProgress() {
        val f = fixture()
        val failing =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    Result.Failed(Failure(FailureKind.UNAVAILABLE, "journal_unavailable"))
            }
        rejected(
            commands(events = failing)
                .start(
                    actor(f),
                    UUID.randomUUID(),
                    selection(),
                    ReportExportDataset.SUMMARY,
                    browserClient,
                ),
            "journal_unavailable",
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from background_jobs where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val id = start(f)
        val l = lease(f, id)
        rejected(worker(Provider(), events = failing).execute(actor(f), l), "journal_unavailable")
        assertEquals(ReportExportStatus.QUEUED, find(f, id).status)
        assertNull(value(transactions.run(actor(f)) { snapshots.find(f.company, f.account, id) }))
        assertEquals(
            0,
            value(transactions.run(actor(f)) { jobs.find(f.company, l.job.request.id) })!!
                .completedItems,
        )
        val provider = Provider()
        val task = worker(provider)
        value(task.execute(actor(f), l))
        value(task.execute(actor(f), l))
        val accepted = find(f, id)
        rejected(worker(provider, events = failing).execute(actor(f), l), "journal_unavailable")
        assertEquals(accepted, find(f, id))
        assertEquals(
            JobStatus.RUNNING,
            value(transactions.run(actor(f)) { jobs.find(f.company, l.job.request.id) })!!.status,
        )
        assertEquals(1, provider.objects.size)
        assertTrue(value(task.execute(actor(f), l)).finished)
    }
}
