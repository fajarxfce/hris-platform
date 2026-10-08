package dev.fajar.hris.worker.runtime

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.usecases.DeferJob
import org.slf4j.LoggerFactory

class JobRunExecutor(private val batch: BatchJobExecutor, private val defer: DeferJob) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(run: RunningJob, task: JobTask) {
        run.attachThread()
        try {
            val outcome =
                try {
                    if (run.lease.job.cancellationRequested) {
                        run.cancel(JobCancellation.REQUESTED)
                        Result.Failed(Failure(FailureKind.CONFLICT, "job_cancellation_requested"))
                    } else batch.execute(run.lease, task)
                } catch (error: InterruptedException) {
                    Result.Failed(Failure(FailureKind.UNAVAILABLE, "job_interrupted"))
                } catch (error: java.util.concurrent.CancellationException) {
                    Result.Failed(Failure(FailureKind.UNAVAILABLE, "job_interrupted"))
                }
            if (outcome is Result.Success) return
            val failure = (outcome as Result.Failed).failure
            // This owner performs bounded cleanup after interruption, then restores the signal.
            val interrupted = Thread.interrupted()
            try {
                if (
                    run.cancellationReason == JobCancellation.LEASE_LOST ||
                        failure.code == "job_lease_lost"
                )
                    return
                val cancelled =
                    run.cancellationReason == JobCancellation.REQUESTED ||
                        failure.code == "job_cancellation_requested"
                if (!cancelled) {
                    val retried = defer.execute(run.lease, failure)
                    if (retried is Result.Success && retried.value) return
                    if (retried is Result.Failed) {
                        logger.warn(
                            "Job deferral failed job={} code={}",
                            run.lease.job.request.id,
                            retried.failure.code,
                        )
                        return
                    }
                }
                val aborted = task.abort(run.lease, failure)
                if (aborted is Result.Failed)
                    logger.warn(
                        "Job cleanup failed job={} code={}",
                        run.lease.job.request.id,
                        aborted.failure.code,
                    )
            } finally {
                if (interrupted) Thread.currentThread().interrupt()
            }
        } finally {
            run.detachThread()
        }
    }
}
