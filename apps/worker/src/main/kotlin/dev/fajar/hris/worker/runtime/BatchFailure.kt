package dev.fajar.hris.worker.runtime

import dev.fajar.hris.core.domain.*
import org.slf4j.LoggerFactory

fun batchFailure(error: Throwable?): Failure {
    val causes = generateSequence(error) { it.cause }.take(16).toList()
    causes.filterIsInstance<BatchStepException>().firstOrNull()?.let {
        return it.failure
    }
    if (
        causes.any {
            it is InterruptedException || it is java.util.concurrent.CancellationException
        }
    )
        return Failure(FailureKind.UNAVAILABLE, "job_interrupted")
    if (causes.any { it is org.springframework.batch.core.job.JobInterruptedException })
        return Failure(FailureKind.CONFLICT, "job_cancellation_requested")
    LoggerFactory.getLogger("dev.fajar.hris.worker")
        .warn("Batch infrastructure failed category={}", error?.javaClass?.name ?: "unknown")
    return if (causes.any { it is org.springframework.dao.TransientDataAccessException })
        Failure(FailureKind.UNAVAILABLE, "batch_temporarily_unavailable")
    else Failure(FailureKind.UNEXPECTED, "batch_execution_failed")
}
