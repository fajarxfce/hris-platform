package dev.fajar.hris.worker.tasks

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.worker.runtime.JobTask

class LeaveCarryExpiryTask(
    private val advance: AdvanceLeaveCarryExpiry,
    private val abort: AbortLeaveCarryExpiry,
) : JobTask {
    override val kind = JobKind.LEAVE_CARRY_EXPIRY

    override fun advance(lease: JobLease): Result<JobStep> = advance.execute(lease)

    override fun abort(lease: JobLease, failure: Failure): Result<Unit> =
        abort.execute(lease, failure)
}
