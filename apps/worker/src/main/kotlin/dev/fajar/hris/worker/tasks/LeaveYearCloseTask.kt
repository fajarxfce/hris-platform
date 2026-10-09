package dev.fajar.hris.worker.tasks

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.usecases.ResolveActor
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.worker.runtime.JobTask

class LeaveYearCloseTask(
    private val resolveActor: ResolveActor,
    private val advance: AdvanceLeaveYearCloseBatch,
    private val abort: AbortLeaveBatch,
) : JobTask {
    override val kind = JobKind.LEAVE_YEAR_CLOSE

    override fun advance(lease: JobLease): Result<JobStep> {
        val request = lease.job.request
        return resolveActor
            .execute(
                request.actorId,
                request.companyId,
                request.authenticatedAt,
                request.correlationId,
                credentialVersion = request.credentialVersion,
                requireAssurance = false,
            )
            .flatMap { advance.execute(it, lease) }
    }

    override fun abort(lease: JobLease, failure: Failure): Result<Unit> =
        abort.execute(lease, failure)
}
