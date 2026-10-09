package dev.fajar.hris.worker.tasks

import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.usecases.ResolveActor
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.worker.runtime.JobTask

class AnnouncementPublicationTask(
    private val resolve: ResolveActor,
    private val advance: AdvanceAnnouncementPublication,
    private val abort: AbortAnnouncementPublication,
) : JobTask {
    override val kind = JobKind.ANNOUNCEMENT_PUBLISH

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
