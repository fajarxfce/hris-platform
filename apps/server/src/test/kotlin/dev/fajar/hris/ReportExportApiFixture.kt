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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Actual admission, snapshot, lease and cleanup transactions; the private provider is controlled.
 */
abstract class ReportExportApiFixture : GroupHeadcountApiFixture() {
    @Autowired protected lateinit var exports: ReportExportRepository
    @Autowired protected lateinit var snapshots: ReportSnapshotRepository
    @Autowired protected lateinit var source: ReportSnapshotSourceRepository
    @Autowired protected lateinit var headcount: HeadcountReportRepository
    @Autowired protected lateinit var attendance: AttendanceExceptionReportRepository
    @Autowired protected lateinit var leave: LeaveReportRepository
    @Autowired protected lateinit var expenses: ExpenseReportRepository
    @Autowired protected lateinit var overtime: OvertimeReportRepository
    @Autowired protected lateinit var payroll: PayrollCostReportRepository
    @Autowired protected lateinit var closings: AttendanceClosingReportRepository
    @Autowired protected lateinit var jobs: JobRepository
    @Autowired protected lateinit var policies: CompanyClientPolicyRepository
    @Autowired protected lateinit var companies: CompanyRepository
    @Autowired protected lateinit var members: MembershipRepository
    @Autowired protected lateinit var identities: IdentityRepository
    @Autowired protected lateinit var cleanup: ObjectCleanupRepository
    @Autowired protected lateinit var journal: ChangeJournalRepository
    @Autowired protected lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var sources: ReportSourceTransactionRunner
    @Autowired protected lateinit var operations: OperationRepository
    @Autowired protected lateinit var security: IdentitySecurityPolicy
    @Autowired protected lateinit var manage: ManageReportExports
    @Autowired protected lateinit var abort: AbortReportExport
    @Autowired protected lateinit var defaultStorage: ObjectStorageRepository

    @org.junit.jupiter.api.BeforeEach
    fun alignExportDeadlineWithDatabase() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
    }

    protected val browserClient = ClientRequest(null, false)

    protected fun <T> value(result: Result<T>): T {
        assertTrue(result is Result.Success, "$result")
        return (result as Result.Success).value
    }

    protected fun rejected(result: Result<*>, code: String? = null) {
        assertTrue(result is Result.Failed, "$result")
        if (code != null) assertEquals(code, (result as Result.Failed).failure.code)
    }

    protected fun selection(kind: ReportKind = ReportKind.HEADCOUNT) =
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

    protected fun actor(f: Fixture) =
        f.actor.copy(
            permissions = ReportKind.entries.flatMap { reportExportSourcePermissions(it) }.toSet()
        )

    protected fun start(
        f: Fixture,
        input: ReportSelection = selection(),
        dataset: ReportExportDataset = ReportExportDataset.SUMMARY,
    ) = value(manage.start(actor(f), UUID.randomUUID(), input, dataset, browserClient)).id

    protected fun find(f: Fixture, id: UUID) =
        value(transactions.run(actor(f)) { exports.find(f.company, f.account, id) })!!

    protected fun parts(f: Fixture, id: UUID) =
        value(transactions.run(actor(f)) { exports.parts(f.company, f.account, id) })

    protected fun lease(f: Fixture, id: UUID): JobLease {
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

    protected fun worker(
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

    protected fun commands(
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

    protected inner class Provider : ObjectStorageRepository by defaultStorage {
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

    protected fun digest(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    protected fun finish(
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

    protected fun revoke(f: Fixture, permission: String) {
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                f.company,
                f.account,
                permission,
            )
    }

    protected fun exportFixture(): Fixture {
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
}
