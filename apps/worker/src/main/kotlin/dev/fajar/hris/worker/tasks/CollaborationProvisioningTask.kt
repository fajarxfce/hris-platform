package dev.fajar.hris.worker.tasks

import dev.fajar.hris.collaboration.domain.usecases.AbortCollaborationProvisioning
import dev.fajar.hris.collaboration.domain.usecases.AdvanceNativeCollaborationProvisioning
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.worker.runtime.JobTask

class CollaborationProvisioningTask(
    private val advance: AdvanceNativeCollaborationProvisioning,
    private val abort: AbortCollaborationProvisioning,
) : JobTask {
    override val kind = JobKind.COLLABORATION_PROVISION

    override fun advance(lease: JobLease): Result<JobStep> = advance.execute(lease)

    override fun abort(lease: JobLease, failure: Failure): Result<Unit> =
        abort.execute(lease, failure)
}
