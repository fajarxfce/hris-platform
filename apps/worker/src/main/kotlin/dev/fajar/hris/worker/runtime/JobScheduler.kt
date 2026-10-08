package dev.fajar.hris.worker.runtime

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.LeaseHealth
import dev.fajar.hris.jobs.domain.usecases.*
import java.util.UUID
import java.util.concurrent.*
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle

class JobScheduler(
    tasks: List<JobTask>,
    private val lease: LeaseJobs,
    private val keepAlive: KeepJobAlive,
    private val defer: DeferJob,
    private val run: JobRunExecutor,
    private val settings: WorkerSettings,
) : SmartLifecycle {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val owner = UUID.randomUUID()
    private val tasks = tasks.associateBy { it.kind }.also { require(it.size == tasks.size) }
    private val active = ConcurrentHashMap<UUID, RunningJob>()
    private val workers =
        ThreadPoolExecutor(
            settings.concurrency,
            settings.concurrency,
            0,
            TimeUnit.MILLISECONDS,
            SynchronousQueue(),
            Thread.ofPlatform().name("hris-job-", 0).factory(),
            ThreadPoolExecutor.AbortPolicy(),
        )
    private val timers =
        ScheduledThreadPoolExecutor(
                2,
                Thread.ofPlatform().name("hris-worker-control-", 0).factory(),
            )
            .apply {
                removeOnCancelPolicy = true
                setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
                setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            }
    @Volatile private var running = false

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = Int.MAX_VALUE

    @Synchronized
    override fun start() {
        if (running) return
        check(!timers.isShutdown) { "A stopped worker requires a new application instance" }
        check(tasks.isNotEmpty()) { "At least one job task must be registered" }
        running = true
        timers.scheduleWithFixedDelay(
            ::poll,
            0,
            settings.pollInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
        timers.scheduleWithFixedDelay(
            ::heartbeat,
            settings.heartbeatInterval.toMillis(),
            settings.heartbeatInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    @Synchronized
    override fun stop() {
        running = false
        active.values.forEach { it.cancel(JobCancellation.SHUTDOWN) }
        timers.shutdownNow()
        workers.shutdown()
        val started = System.nanoTime()
        try {
            if (
                !workers.awaitTermination(
                    settings.shutdownTimeout.toMillis(),
                    TimeUnit.MILLISECONDS,
                )
            ) {
                workers.shutdownNow()
                logger.warn("Worker shutdown reached timeout activeJobs={}", active.size)
            }
            val remaining = settings.shutdownTimeout.toNanos() - (System.nanoTime() - started)
            if (remaining > 0) timers.awaitTermination(remaining, TimeUnit.NANOSECONDS)
        } catch (error: InterruptedException) {
            workers.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private fun poll() {
        if (!running || active.size >= settings.concurrency) return
        try {
            val leased =
                lease.execute(
                    owner,
                    settings.concurrency - active.size,
                    settings.leaseSeconds,
                    tasks.keys,
                )
            if (leased is Result.Failed) {
                logger.warn("Job leasing failed code={}", leased.failure.code)
                return
            }
            for (candidate in (leased as Result.Success).value) {
                if (!running) return // Unstarted leases are recovered after expiry.
                val executing = RunningJob(candidate)
                val existing = active.putIfAbsent(candidate.job.request.id, executing)
                if (existing != null) {
                    existing.cancel(JobCancellation.LEASE_LOST)
                    defer.execute(candidate, Failure(FailureKind.UNAVAILABLE, "worker_slot_busy"))
                    continue
                }
                try {
                    workers.execute {
                        try {
                            run.execute(executing, tasks.getValue(candidate.job.request.kind))
                        } catch (error: Exception) {
                            logger.warn(
                                "Job adapter failed job={} category={}",
                                candidate.job.request.id,
                                error.javaClass.name,
                            )
                        } finally {
                            active.remove(candidate.job.request.id, executing)
                        }
                    }
                } catch (error: RejectedExecutionException) {
                    active.remove(candidate.job.request.id, executing)
                    defer.execute(candidate, Failure(FailureKind.UNAVAILABLE, "worker_slot_busy"))
                }
            }
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            if (running) logger.warn("Job polling failed category={}", error.javaClass.name)
        }
    }

    private fun heartbeat() {
        if (!running) return
        try {
            for (executing in active.values) {
                if (executing.exceededBudget(settings.maximumRunTime)) {
                    executing.cancel(JobCancellation.TIME_LIMIT)
                    continue
                }
                when (val result = keepAlive.execute(executing.lease, settings.leaseSeconds)) {
                    is Result.Failed ->
                        logger.warn(
                            "Job heartbeat failed job={} code={}",
                            executing.lease.job.request.id,
                            result.failure.code,
                        )
                    is Result.Success ->
                        when (result.value) {
                            LeaseHealth.ACTIVE,
                            LeaseHealth.FINISHED -> Unit
                            LeaseHealth.CANCELLATION_REQUESTED ->
                                executing.cancel(JobCancellation.REQUESTED)
                            LeaseHealth.LOST -> executing.cancel(JobCancellation.LEASE_LOST)
                        }
                }
            }
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            if (running) logger.warn("Job heartbeat failed category={}", error.javaClass.name)
        }
    }
}
