package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.reporting.domain.entities.ReportExportStatus
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.ReportExportRepository
import java.time.Clock
import java.time.temporal.ChronoUnit

/** Fenced cleanup can stop work even after its originating account loses access. */
class AbortReportExport(
    private val exports: ReportExportRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val resource = reportExportJobResource(lease.job.request)
        if (resource is Result.Failed) return resource
        val id = (resource as Result.Success).value
        val request = lease.job.request
        val actor =
            Actor(
                request.actorId,
                request.companyId,
                emptySet(),
                request.authenticatedAt,
                request.correlationId,
            )
        return transactions.run(actor) {
            val owned = jobs.lockLease(lease)
            if (owned is Result.Failed) return@run owned
            val job = (owned as Result.Success).value ?: return@run Result.Success(Unit)
            if (job.request != request)
                return@run exportExecutionFailure("job_scope_mismatch", FailureKind.FORBIDDEN)
            val owner = exports.lockOwner(request.companyId, request.actorId)
            if (owner is Result.Failed) return@run owner
            val found = exports.find(request.companyId, request.actorId, id, true)
            if (found is Result.Failed) return@run found
            val current = (found as Result.Success).value
            val cancelled = job.cancellationRequested
            val code =
                if (cancelled) "report_export_cancelled"
                else
                    failure.code.takeIf { it.matches(Regex("[a-z][a-z0-9_]{0,79}")) }
                        ?: "report_export_failed"
            if (
                current != null &&
                    current.jobId == request.id &&
                    current.status in setOf(ReportExportStatus.QUEUED, ReportExportStatus.RENDERING)
            ) {
                val stopped =
                    exports
                        .stop(
                            current,
                            code,
                            maxOf(clock.instant().truncatedTo(ChronoUnit.MICROS), current.updatedAt),
                        )
                        .flatMap { exportChanged(it) }
                if (stopped is Result.Failed) return@run stopped
            }
            jobs
                .complete(lease, if (cancelled) JobStatus.CANCELLED else JobStatus.FAILED, code)
                .flatMap { exportChanged(it, "job_lease_lost") }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "report_export",
                            id,
                            "reporting.export_stopped",
                            mapOf("jobId" to request.id.toString(), "failureCode" to code),
                        ),
                    )
                }
        }
    }
}
