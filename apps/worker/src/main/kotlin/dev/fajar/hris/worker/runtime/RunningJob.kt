package dev.fajar.hris.worker.runtime

import dev.fajar.hris.jobs.domain.entities.JobLease
import java.time.Duration

/**
 * Synchronization prevents a late cancellation from interrupting a thread reused by another job.
 */
class RunningJob(val lease: JobLease) {
    private var thread: Thread? = null
    private val started = System.nanoTime()
    @Volatile private var cancellation: JobCancellation? = null
    val cancellationReason: JobCancellation?
        get() = cancellation

    fun exceededBudget(limit: Duration): Boolean = System.nanoTime() - started >= limit.toNanos()

    @Synchronized
    fun attachThread() {
        check(thread == null)
        thread = Thread.currentThread()
        if (cancellation != null) thread?.interrupt()
    }

    @Synchronized
    fun detachThread() {
        if (thread === Thread.currentThread()) thread = null
    }

    @Synchronized
    fun cancel(reason: JobCancellation) {
        if (cancellation == null) cancellation = reason
        thread?.interrupt()
    }
}
