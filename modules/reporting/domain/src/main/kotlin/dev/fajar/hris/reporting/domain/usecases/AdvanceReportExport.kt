package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.*
import dev.fajar.hris.storage.domain.entities.ObjectCleanupRequest
import dev.fajar.hris.storage.domain.repositories.*
import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

/** One bounded capture, upload or publication. Provider I/O never owns a SQL transaction. */
class AdvanceReportExport(
    private val exports: ReportExportRepository,
    private val snapshots: ReportSnapshotRepository,
    private val detailSources: ReportSnapshotSourceRepository,
    private val headcount: HeadcountReportRepository,
    private val attendance: AttendanceExceptionReportRepository,
    private val leave: LeaveReportRepository,
    private val expenses: ExpenseReportRepository,
    private val overtime: OvertimeReportRepository,
    private val payroll: PayrollCostReportRepository,
    private val closings: AttendanceClosingReportRepository,
    private val jobs: JobRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val storage: ObjectStorageRepository,
    private val cleanup: ObjectCleanupRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val sources: ReportSourceTransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val resource = validateReportExportJobActor(actor, lease.job.request)
        if (resource is Result.Failed) return resource
        val id = (resource as Result.Success).value
        // This first read discovers only immutable scope; all facts require the fenced guards
        // below.
        val observed =
            transactions.run(actor) {
                exports.find(requireNotNull(actor.companyId), actor.accountId, id)
            }
        if (observed is Result.Failed) return observed
        val export =
            (observed as Result.Success).value
                ?: return exportExecutionFailure("report_export_not_found", FailureKind.NOT_FOUND)
        val prepared =
            guarded(actor, lease, export.request) { current ->
                when (current.status) {
                    ReportExportStatus.QUEUED ->
                        capture(actor, lease, current).map { Prepared.Step(it) }
                    ReportExportStatus.RENDERING -> render(actor, lease, current)
                    else -> exportExecutionFailure("report_export_not_running")
                }
            }
        if (prepared is Result.Failed) return prepared
        val work = (prepared as Result.Success).value
        if (work is Prepared.Step) return Result.Success(work.value)
        work as Prepared.Upload
        val key = reportExportObjectKey(work.upload)
        val stored = storage.put(key, work.bytes)
        if (stored is Result.Failed) return stored
        val evidence = (stored as Result.Success).value
        if (
            evidence.key != key ||
                evidence.size != work.upload.bytes.toLong() ||
                evidence.sha256 != work.upload.sha256 ||
                evidence.etag.isBlank() ||
                evidence.etag.length > 200 ||
                evidence.etag.any(Char::isISOControl)
        )
            return exportExecutionFailure(
                "invalid_object_storage_response",
                FailureKind.UNAVAILABLE,
            )
        return guarded(actor, lease, export.request) { current ->
            if (current.status != ReportExportStatus.RENDERING || current.version != work.version)
                return@guarded exportExecutionFailure("stale_version")
            val at = maxOf(now(), current.updatedAt)
            exports
                .accept(current, ReportExportPart(work.upload, evidence.etag), at)
                .flatMap { exportChanged(it) }
                .flatMap { checkpoint(lease, current.partCount + 2) }
        }
    }

    private fun capture(actor: Actor, lease: JobLease, current: ReportExport): Result<JobStep> {
        val request = current.request
        val columns = reportExportColumns(request.selection.kind, request.dataset)
        if (columns is Result.Failed) return columns
        val at = maxOf(now(), current.updatedAt)
        val capture =
            ReportSnapshotCapture(
                request.id,
                request.companyId,
                request.accountId,
                request.selection,
                request.dataset,
                (columns as Result.Success).value,
                at,
                request.expiresAt,
                request.schemaVersion,
                request.formatVersion,
            )
        val begun = snapshots.begin(capture)
        if (begun is Result.Failed) return begun
        if (!(begun as Result.Success).value)
            return exportExecutionFailure("report_snapshot_capture_changed")
        val rows =
            if (request.dataset == ReportExportDataset.DETAIL)
                detailSources.captureDetails(capture).map { Unit }
            else summary(capture).flatMap { snapshots.append(capture, 0, it) }
        if (rows is Result.Failed) return rows
        return snapshots
            .seal(capture)
            .flatMap { validateReportExportSnapshot(request, it) }
            .flatMap { exports.captured(current, at) }
            .flatMap { exportChanged(it) }
            .flatMap {
                journal.record(
                    actor,
                    ChangeRecord(
                        "report_export",
                        request.id,
                        "reporting.export_captured",
                        mapOf("jobId" to current.jobId.toString()),
                    ),
                )
            }
            .flatMap { checkpoint(lease, 1) }
    }

    private fun render(actor: Actor, lease: JobLease, current: ReportExport): Result<Prepared> {
        val request = current.request
        val found = snapshots.find(request.companyId, request.accountId, request.id)
        if (found is Result.Failed) return found
        val snapshot =
            (found as Result.Success).value
                ?: return exportExecutionFailure("report_snapshot_not_found")
        val valid = validateReportExportSnapshot(request, snapshot)
        if (valid is Result.Failed) return valid
        if (current.completedRows == snapshot.rows && current.partCount > 0) {
            val parts = exports.parts(request.companyId, request.accountId, request.id)
            if (parts is Result.Failed) return parts
            val assembled = reportExportManifest(snapshot, (parts as Result.Success).value)
            if (assembled is Result.Failed) return assembled
            val manifest = (assembled as Result.Success).value
            if (manifest.rows != current.completedRows || manifest.bytes != current.byteCount)
                return exportExecutionFailure("report_export_manifest_changed")
            val at = maxOf(now(), current.updatedAt)
            return exports
                .publish(current, manifest.tag, at)
                .flatMap { exportChanged(it) }
                .flatMap { checkpoint(lease, current.partCount + 2) }
                .flatMap { jobs.complete(lease, JobStatus.SUCCEEDED) }
                .flatMap { exportChanged(it, "job_lease_lost") }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "report_export",
                            request.id,
                            "reporting.export_ready",
                            mapOf("jobId" to current.jobId.toString()),
                        ),
                    )
                }
                .map { Prepared.Step(JobStep(current.partCount + 2, true)) }
        }
        if (
            current.completedRows > snapshot.rows ||
                current.partCount >= REPORT_EXPORT_MAX_PARTS ||
                current.reservationCount >= REPORT_EXPORT_MAX_RESERVATIONS
        )
            return exportExecutionFailure("report_export_part_limit")
        val loaded = snapshots.rows(snapshot, current.completedRows, REPORT_CSV_MAX_PART_ROWS)
        if (loaded is Result.Failed) return loaded
        val page = (loaded as Result.Success).value
        if (
            page.size > REPORT_CSV_MAX_PART_ROWS ||
                page.size > snapshot.rows - current.completedRows ||
                page.withIndex().any { (i, row) -> row.number != current.completedRows + i + 1 } ||
                (page.isEmpty() && current.completedRows < snapshot.rows)
        )
            return exportExecutionFailure("report_snapshot_rows_changed")
        val encoded =
            encodeReportCsvPart(
                snapshot.capture.columns,
                page.map { it.cells },
                current.partCount == 0,
            )
        if (encoded is Result.Failed) return encoded
        val part = (encoded as Result.Success).value
        val bytes = part.bytes()
        if (
            current.byteCount + bytes.size > REPORT_SNAPSHOT_MAX_BYTES ||
                current.reservedBytes + bytes.size > REPORT_EXPORT_MAX_RESERVED_BYTES
        )
            return exportExecutionFailure("report_export_byte_limit")
        val upload =
            ReportExportUpload(
                UUID.randomUUID(),
                request.companyId,
                request.accountId,
                request.id,
                current.partCount + 1,
                current.completedRows,
                part.rows,
                current.byteCount,
                bytes.size,
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it)
                },
                maxOf(now(), current.updatedAt),
            )
        val reservation =
            ObjectCleanupRequest(
                upload.id,
                request.companyId,
                request.id,
                reportExportObjectKey(upload),
                bytes.size.toLong(),
                request.accountId,
                request.expiresAt.plus(REPORT_EXPORT_DELETE_GRACE),
            )
        return cleanup
            .schedule(reservation)
            .flatMap { exports.reserve(current, upload) }
            .flatMap { exportChanged(it) }
            .map { Prepared.Upload(upload, bytes, current.version + 1) }
    }

    private fun summary(capture: ReportSnapshotCapture): Result<List<List<ReportExportCell>>> {
        val p = capture.selection.parameters
        val company = capture.companyId
        return when (capture.selection.kind) {
            ReportKind.HEADCOUNT ->
                headcount.countGroup(p.companies.toSet(), LocalDate.parse(p.asOf)).flatMap {
                    reportSummaryRows(capture, it)
                }
            ReportKind.ATTENDANCE_EXCEPTIONS ->
                attendance
                    .aggregate(company, LocalDate.parse(p.from), LocalDate.parse(p.until))
                    .flatMap { reportSummaryRows(capture, it) }
            ReportKind.LEAVE ->
                leave
                    .aggregate(company, LocalDate.parse(p.from), LocalDate.parse(p.until))
                    .flatMap { reportSummaryRows(capture, it) }
            ReportKind.EXPENSES ->
                expenses
                    .aggregate(company, LocalDate.parse(p.from), LocalDate.parse(p.until))
                    .flatMap { reportSummaryRows(capture, it) }
            ReportKind.OVERTIME ->
                overtime
                    .aggregate(
                        company,
                        LocalDate.parse(p.from),
                        LocalDate.parse(p.until),
                        p.status?.let(OvertimeStatus::valueOf),
                    )
                    .flatMap { reportSummaryRows(capture, it) }
            ReportKind.PAYROLL_COSTS ->
                payroll
                    .aggregate(company, YearMonth.parse(p.from), YearMonth.parse(p.until))
                    .flatMap { reportSummaryRows(capture, it) }
            ReportKind.ATTENDANCE_CLOSINGS ->
                closings
                    .aggregate(company, YearMonth.parse(p.from), YearMonth.parse(p.until))
                    .flatMap { reportSummaryRows(capture, it) }
        }
    }

    private fun <T> guarded(
        actor: Actor,
        lease: JobLease,
        request: ReportExportRequest,
        operation: (ReportExport) -> Result<T>,
    ): Result<T> =
        sources.runWithReportSources(actor, reportExportCompanies(request)) {
            val owned = liveLease(lease)
            if (owned is Result.Failed) return@runWithReportSources owned
            val owner = exports.lockOwner(request.companyId, request.accountId)
            if (owner is Result.Failed) return@runWithReportSources owner
            val found = exports.find(request.companyId, request.accountId, request.id, true)
            if (found is Result.Failed) return@runWithReportSources found
            val current =
                (found as Result.Success).value
                    ?: return@runWithReportSources exportExecutionFailure(
                        "report_export_not_found",
                        FailureKind.NOT_FOUND,
                    )
            if (current.request != request || current.jobId != lease.job.request.id)
                return@runWithReportSources exportExecutionFailure(
                    "job_scope_mismatch",
                    FailureKind.FORBIDDEN,
                )
            val ordered = request.sources.keys.sorted()
            for (company in ordered) {
                val r = policies.lock(company, shared = true)
                if (r is Result.Failed) return@runWithReportSources r
            }
            for (company in ordered) {
                val r = companies.lock(company, shared = true)
                if (r is Result.Failed) return@runWithReportSources r
            }
            for (company in ordered) {
                val r = members.lock(company, shared = true)
                if (r is Result.Failed) return@runWithReportSources r
            }
            val account = identities.lockAccount(actor.accountId, shared = true)
            if (account is Result.Failed) return@runWithReportSources account
            val access = checkAccess(actor, current)
            if (access is Result.Failed) return@runWithReportSources access
            operation(current).flatMap { value ->
                // A scheduled policy transition or lease deadline can pass while source work runs.
                checkAccess(actor, current)
                    .flatMap {
                        if (value is Prepared.Step && value.value.finished) Result.Success(Unit)
                        else liveLease(lease).map { Unit }
                    }
                    .map { value }
            }
        }

    private fun checkAccess(actor: Actor, current: ReportExport): Result<Unit> {
        val request = current.request
        for ((company, permissions) in request.sources.toSortedMap()) {
            val allowed =
                identities
                    .access(actor.accountId, company)
                    .flatMap {
                        validateCompanyCommandActor(
                            reportExportScope(actor, company, permissions),
                            it,
                        )
                    }
                    .flatMap { validateReportKindAccess(it, request.selection.kind) }
            if (allowed is Result.Failed) return allowed
            val at = clock.instant()
            val available =
                policies.effective(company, at).flatMap {
                    validateClientAvailability(
                        clientPolicyStatus(it, at),
                        setOf(CompanyModule.REPORTING),
                        request.client.version,
                        request.client.native,
                    )
                }
            if (available is Result.Failed)
                return Result.Failed(
                    available.failure.copy(
                        parameters =
                            available.failure.parameters + ("companyId" to company.toString())
                    )
                )
        }
        return validateReportExportLifetime(current, clock.instant())
    }

    private fun liveLease(lease: JobLease): Result<BackgroundJob> =
        jobs.lockLease(lease).flatMap {
            when {
                it == null -> exportExecutionFailure("job_lease_lost")
                it.request != lease.job.request ->
                    exportExecutionFailure("job_scope_mismatch", FailureKind.FORBIDDEN)
                it.cancellationRequested -> exportExecutionFailure("job_cancellation_requested")
                else -> Result.Success(it)
            }
        }

    private fun checkpoint(lease: JobLease, completed: Int): Result<JobStep> =
        jobs
            .checkpoint(lease, JobProgress(completed, emptyMap()))
            .flatMap { exportChanged(it, "job_lease_lost") }
            .map { JobStep(completed, false) }

    private fun now() = clock.instant().truncatedTo(ChronoUnit.MICROS)

    private sealed interface Prepared {
        data class Step(val value: JobStep) : Prepared

        data class Upload(val upload: ReportExportUpload, val bytes: ByteArray, val version: Long) :
            Prepared
    }
}
