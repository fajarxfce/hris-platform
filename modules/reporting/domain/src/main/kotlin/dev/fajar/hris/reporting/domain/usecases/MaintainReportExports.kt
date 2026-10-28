package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.*
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

/** A bounded pass owns no report authority and never decodes retained business facts. */
class MaintainReportExports(
    private val expiry: ReportExportExpiryRepository,
    private val exports: ReportExportRepository,
    private val snapshots: ReportSnapshotRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(limit: Int = 4): Result<ReportExportCleanupResult> {
        if (limit !in 1..4)
            return exportExecutionFailure("invalid_cleanup_limit", FailureKind.VALIDATION)
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val worker = Actor(UUID(0, 0), null, emptySet(), now(), UUID.randomUUID())
        val due = transactions.run(worker) { expiry.due(now(), limit) }
        if (due is Result.Failed) return due
        var processed = 0
        var deleted = 0
        var retired = 0
        var failure: Result.Failed? = null
        for (key in (due as Result.Success).value) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val result = prune(key)
            if (result is Result.Failed) {
                if (failure == null) failure = result
                // Scheduling has no content/job effects and commits independently of failed work.
                val scope =
                    Actor(key.accountId, key.companyId, emptySet(), now(), UUID.randomUUID())
                val retried =
                    transactions.run(scope) {
                        expiry.retry(key, maxOf(key.expiresAt, now().plusSeconds(60)))
                    }
                if (retried is Result.Failed) failure = retried
                continue
            }
            val value = (result as Result.Success).value
            processed += value.processed
            deleted += value.deletedRows
            retired += value.retired
        }
        return failure ?: Result.Success(ReportExportCleanupResult(processed, deleted, retired))
    }

    private fun prune(key: ReportExportExpiryKey): Result<ReportExportCleanupResult> {
        val actor = Actor(key.accountId, key.companyId, emptySet(), now(), UUID.randomUUID())
        val observed = transactions.run(actor) { expiry.inspect(key) }
        if (observed is Result.Failed) return observed
        val old =
            (observed as Result.Success).value
                ?: return exportExecutionFailure("report_export_not_found", FailureKind.NOT_FOUND)
        return transactions.run(actor) {
            val job = jobs.find(key.companyId, old.jobId, true)
            if (job is Result.Failed) return@run job
            val owner = exports.lockOwner(key.companyId, key.accountId)
            if (owner is Result.Failed) return@run owner
            val found = expiry.inspect(key, true)
            if (found is Result.Failed) return@run found
            var current =
                (found as Result.Success).value
                    ?: return@run exportExecutionFailure(
                        "report_export_not_found",
                        FailureKind.NOT_FOUND,
                    )
            if (current.jobId != old.jobId || current.expiresAt != key.expiresAt)
                return@run exportExecutionFailure("stale_version")
            val owned = expiry.lockDue(key, now())
            if (owned is Result.Failed) return@run owned
            if (!(owned as Result.Success).value)
                return@run Result.Success(ReportExportCleanupResult(0, 0, 0))
            if (current.retiredAt != null)
                return@run expiry.remove(key).map { ReportExportCleanupResult(1, 0, 0) }
            if (current.expiresAt > clock.instant())
                return@run exportExecutionFailure("report_export_not_expired")
            val background = (job as Result.Success).value
            if (
                background == null ||
                    background.request.actorId != key.accountId ||
                    background.request.kind != JobKind.REPORT_EXPORT ||
                    background.request.values["exportId"] != key.id.toString()
            )
                return@run exportExecutionFailure("report_export_job_missing")
            val at = maxOf(now(), current.updatedAt)
            if (
                background.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING) &&
                    !background.cancellationRequested
            ) {
                val cancel =
                    jobs.requestCancellation(key.companyId, current.jobId, background.version, at)
                if (cancel is Result.Failed) return@run cancel
                if ((cancel as Result.Success).value == null)
                    return@run exportExecutionFailure("stale_version")
            }
            if (current.status in setOf(ReportExportStatus.QUEUED, ReportExportStatus.RENDERING)) {
                val stop = expiry.stop(current, at).flatMap { exportChanged(it) }
                if (stop is Result.Failed) return@run stop
                current =
                    current.copy(
                        status = ReportExportStatus.STOPPED,
                        version = current.version + 1,
                        updatedAt = at,
                    )
                val event =
                    journal.record(
                        actor,
                        ChangeRecord("report_export", key.id, "reporting.export_expired"),
                    )
                if (event is Result.Failed) return@run event
            }
            val rows = snapshots.deleteExpiredRows(key.companyId, key.accountId, key.id, at, 500)
            if (rows is Result.Failed) return@run rows
            val count = (rows as Result.Success).value
            val completed = expiry.retire(current, at)
            if (completed is Result.Failed) return@run completed
            if ((completed as Result.Success).value) {
                journal
                    .record(
                        actor,
                        ChangeRecord("report_export", key.id, "reporting.export_retired"),
                    )
                    .flatMap { expiry.remove(key) }
                    .map { ReportExportCleanupResult(1, count, 1) }
            } else {
                // Move a progressing export behind older due entries; pending objects poll less
                // often.
                expiry
                    .postpone(
                        key,
                        maxOf(key.expiresAt, now().plusSeconds(if (count > 0) 1 else 60)),
                    )
                    .map { ReportExportCleanupResult(1, count, 0) }
            }
        }
    }

    private fun now() = clock.instant().truncatedTo(ChronoUnit.MICROS)
}
