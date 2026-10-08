package dev.fajar.hris.worker.runtime

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.entities.*

/**
 * Feature adapters call use cases; business changes and fenced checkpoints share their transaction.
 */
interface JobTask {
    val kind: JobKind

    fun advance(lease: JobLease): Result<JobStep>

    fun abort(lease: JobLease, failure: Failure): Result<Unit>
}
