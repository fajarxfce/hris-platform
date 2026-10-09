package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*

class AdvanceWorkPeriodClose(
    private val periods: WorkPeriodRepository,
    private val jobs: JobRepository,
    private val schedules: ScheduleRepository,
    private val attendance: AttendanceRepository,
    private val corrections: AttendanceCorrectionRepository,
    private val journal: ChangeJournalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val overtime: OvertimeRepository,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val request = lease.job.request
        if (
            actor.companyId != request.companyId ||
                actor.accountId != request.actorId ||
                actor.credentialVersion != request.credentialVersion ||
                request.kind != JobKind.WORKFORCE_CLOSE
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
        val access = actor.requirePermission("workforce.close")
        if (access is Result.Failed) return access
        val company = request.companyId
        return transactions.run(actor) {
            val current = jobs.lockLease(lease)
            if (current is Result.Failed) return@run current
            val job =
                (current as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            if (job.cancellationRequested)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested")
                )
            val found = periods.forJob(company, request.id, true)
            if (found is Result.Failed) return@run found
            val period = (found as Result.Success).value
            if (period == null || period.status != WorkPeriodStatus.PROCESSING)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "work_period_job_obsolete"))
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permission = live.requirePermission("workforce.close")
            if (permission is Result.Failed) return@run permission
            val cursor = parseWorkPeriodCursor(job.checkpoint)
            if (cursor is Result.Failed) return@run cursor
            val target = periods.nextTarget(company, request.id, (cursor as Result.Success).value)
            if (target is Result.Failed) return@run target
            val employeeId = (target as Result.Success).value
            if (employeeId == null) {
                val count = periods.snapshotCount(company, request.id)
                if (count is Result.Failed) return@run count
                if (
                    (count as Result.Success).value != request.totalItems - 1 ||
                        job.completedItems != request.totalItems - 1
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "work_period_snapshot_incomplete")
                    )
                return@run jobs
                    .checkpoint(lease, JobProgress(request.totalItems, job.checkpoint))
                    .flatMap { changed ->
                        if (!changed)
                            return@flatMap Result.Failed(
                                Failure(FailureKind.CONFLICT, "job_lease_lost")
                            )
                        periods
                            .finish(company, period, clock.instant())
                            .flatMap {
                                jobs.complete(lease, JobStatus.SUCCEEDED).flatMap { finished ->
                                    if (!finished)
                                        Result.Failed(
                                            Failure(FailureKind.CONFLICT, "job_lease_lost")
                                        )
                                    else
                                        journal.record(
                                            actor,
                                            ChangeRecord(
                                                "work_period",
                                                period.id,
                                                "workforce.period_closed",
                                                mapOf(
                                                    "month" to period.month.toString(),
                                                    "jobId" to request.id.toString(),
                                                ),
                                            ),
                                        )
                                }
                            }
                            .map { JobStep(request.totalItems, true) }
                    }
            }
            val from = period.month.atDay(1)
            val until = period.month.atEndOfMonth()
            val calendar =
                schedules.calendar(company, employeeId, from, until).flatMap(::resolveCalendar)
            if (calendar is Result.Failed) return@run calendar
            val entries = attendance.entries(company, employeeId, from, until)
            if (entries is Result.Failed) return@run entries
            val changes = corrections.latest(company, employeeId, from, until)
            if (changes is Result.Failed) return@run changes
            val approvedOvertime = overtime.approved(company, employeeId, period.month)
            if (approvedOvertime is Result.Failed) return@run approvedOvertime
            val snapshot =
                snapshotWorkPeriod(
                    employeeId,
                    period,
                    (calendar as Result.Success).value,
                    (entries as Result.Success).value,
                    (changes as Result.Success).value,
                    (approvedOvertime as Result.Success).value,
                )
            if (snapshot is Result.Failed) return@run snapshot
            periods.saveSnapshot(company, request.id, (snapshot as Result.Success).value).flatMap {
                val progress =
                    JobProgress(
                        job.completedItems + 1,
                        mapOf("afterEmployeeId" to employeeId.toString()),
                    )
                jobs.checkpoint(lease, progress).flatMap { changed ->
                    if (changed) Result.Success(JobStep(progress.completedItems, false))
                    else Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                }
            }
        }
    }
}
