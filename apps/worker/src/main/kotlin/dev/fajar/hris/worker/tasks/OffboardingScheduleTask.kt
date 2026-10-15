package dev.fajar.hris.worker.tasks

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.usecases.ResolveActor
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.people.domain.usecases.*
import dev.fajar.hris.worker.runtime.JobTask

class OffboardingScheduleTask(
    private val resolve: ResolveActor,
    private val execute: ExecuteOffboardingSchedule,
    private val abort: AbortOffboardingSchedule,
) : JobTask {
    override val kind = JobKind.PEOPLE_OFFBOARD

    override fun advance(lease: JobLease): Result<JobStep> {
        val request = lease.job.request
        return resolve
            .execute(
                request.actorId,
                request.companyId,
                request.authenticatedAt,
                request.correlationId,
                credentialVersion = request.credentialVersion,
                requireAssurance = false,
            )
            .flatMap { execute.execute(it, lease) }
    }

    override fun abort(lease: JobLease, failure: Failure): Result<Unit> =
        abort.execute(lease, failure)
}
