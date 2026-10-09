package dev.fajar.hris.worker.tasks

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.usecases.ResolveActor
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.worker.runtime.JobTask

class PayrollFinalizationTask(
    private val resolve: ResolveActor,
    private val advance: AdvancePayrollFinalization,
    private val abort: AbortPayrollFinalization,
) : JobTask {
    override val kind = JobKind.PAYROLL_FINALIZE

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
            .flatMap { advance.execute(it, lease) }
    }

    override fun abort(lease: JobLease, failure: Failure): Result<Unit> =
        abort.execute(lease, failure)
}
