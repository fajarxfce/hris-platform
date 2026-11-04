package dev.fajar.hris.worker.collaboration

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomLease
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/** Bounded native room preparation; the durable lease and outcome remain in PostgreSQL. */
class CollaborationSpaceRoomWorker(
    private val claim: (UUID, Int) -> Result<List<CollaborationSpaceRoomLease>>,
    private val advance: (CollaborationSpaceRoomLease) -> Result<Unit>,
    private val settle: (CollaborationSpaceRoomLease, Failure) -> Result<Boolean>,
    private val tasks: ThreadPoolTaskExecutor,
    private val timers: ThreadPoolTaskScheduler,
    private val settings: CollaborationSpaceRoomWorkerSettings =
        CollaborationSpaceRoomWorkerSettings(),
) : SmartLifecycle {
    private val owner = UUID.randomUUID()
    private val slots = Semaphore(settings.parallelism)
    private val logger = LoggerFactory.getLogger(javaClass)
    @Volatile private var running = false
    private var started = false
    private var stopped = false
    private var polling: ScheduledFuture<*>? = null

    init {
        require(
            tasks.maxPoolSize == settings.parallelism && tasks.corePoolSize == settings.parallelism
        )
        require(tasks.queueCapacity == 0 && timers.scheduledThreadPoolExecutor.corePoolSize >= 2)
    }

    @Synchronized
    override fun start() {
        check(!started && !stopped) { "Group chat room provisioning worker cannot be restarted" }
        started = true
        running = true
        try {
            polling = timers.scheduleWithFixedDelay(Runnable { poll() }, settings.pollInterval)
        } catch (error: RuntimeException) {
            stop()
            throw error
        }
    }

    private fun poll() {
        if (!running) return
        // Reserve pending submissions as well as occupied threads. A cancelled task that is
        // still exiting continues to consume a physical executor slot.
        val capacity =
            minOf(
                slots.availablePermits(),
                (settings.parallelism - tasks.activeCount).coerceAtLeast(0),
            )
        if (capacity == 0 || !slots.tryAcquire(capacity)) return
        var reserved = capacity
        try {
            val result = claim(owner, capacity)
            if (result is Result.Failed) {
                logger.warn("Group chat room claim failed code={}", result.failure.code)
                return
            }
            val leases = (result as Result.Success).value
            if (
                leases.size > capacity ||
                    leases.map { it.token }.distinct().size != leases.size ||
                    leases.any { it.ownerId != owner || it.token == UUID(0, 0) } ||
                    leases
                        .map { it.request.spaceId to it.request.conversationId }
                        .distinct()
                        .size != leases.size
            ) {
                logger.warn("Group chat room claim returned invalid metadata")
                return
            }
            for (lease in leases.take(capacity)) {
                if (!running) return
                val deadline =
                    minOf(
                        lease.until,
                        lease.request.expiresAt,
                        Instant.now().plus(settings.processingTimeout),
                    )
                if (deadline <= Instant.now()) continue
                reserved--
                val task =
                    RoomTask({ slots.release() }) {
                        try {
                            val advanced = advance(lease)
                            if (advanced is Result.Failed) {
                                val resolved = settle(lease, advanced.failure)
                                if (resolved is Result.Failed)
                                    logger.warn(
                                        "Group chat room outcome failed code={}",
                                        resolved.failure.code,
                                    )
                            }
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                        } catch (_: CancellationException) {
                            // Expired fenced leases are recoverable; do not start I/O during
                            // cancellation.
                        } catch (error: Exception) {
                            logger.warn(
                                "Group chat room task stopped category={}",
                                error.javaClass.name,
                            )
                        }
                    }
                try {
                    task.watch(timers.schedule(Runnable { task.cancel(true) }, deadline))
                    tasks.execute(task)
                } catch (error: RuntimeException) {
                    task.cancel(false)
                    if (running)
                        logger.warn(
                            "Group chat room submission stopped category={}",
                            error.javaClass.name,
                        )
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: CancellationException) {
            // Shutdown cancels a pending metadata claim.
        } catch (error: Exception) {
            if (running)
                logger.warn("Group chat room poll stopped category={}", error.javaClass.name)
        } finally {
            slots.release(reserved)
        }
    }

    @Synchronized
    override fun stop() {
        stopped = true
        running = false
        polling?.cancel(true)
        polling = null
        tasks.shutdown()
        timers.shutdown()
    }

    override fun stop(callback: Runnable) {
        try {
            stop()
        } finally {
            callback.run()
        }
    }

    override fun isRunning() = running

    override fun getPhase() = Int.MAX_VALUE - 20

    private class RoomTask(private val release: () -> Unit, operation: () -> Unit) :
        FutureTask<Unit>(Callable { operation() }) {
        private val timer = AtomicReference<ScheduledFuture<*>?>()

        fun watch(value: ScheduledFuture<*>) {
            check(timer.compareAndSet(null, value))
            if (isDone) value.cancel(false)
        }

        override fun done() {
            timer.get()?.cancel(false)
            release()
        }
    }
}
