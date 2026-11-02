package dev.fajar.hris.worker.push

import dev.fajar.hris.collaboration.domain.entities.CollaborationPushLease
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.worker.runtime.DeadlineTask
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledFuture
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/** Owns bounded task/timer pools; retained progress lives in PostgreSQL, not a process registry. */
class CollaborationPushWorker(
    private val claim: (UUID, Int) -> Result<List<CollaborationPushLease>>,
    private val deliver: (CollaborationPushLease) -> Result<Unit>,
    private val tasks: ThreadPoolTaskExecutor,
    private val timers: ThreadPoolTaskScheduler,
    private val settings: CollaborationPushWorkerSettings = CollaborationPushWorkerSettings(),
) : SmartLifecycle {
    private val owner = UUID.randomUUID()
    private val logger = LoggerFactory.getLogger(javaClass)
    @Volatile private var running = false
    private var started = false
    private var polling: ScheduledFuture<*>? = null

    @Synchronized
    override fun start() {
        check(!started) { "Chat push worker cannot be restarted" }
        started = true
        running = true
        try {
            polling = timers.scheduleWithFixedDelay(Runnable { poll() }, settings.pollInterval)
        } catch (error: RuntimeException) {
            running = false
            throw error
        }
    }

    private fun poll() {
        if (!running) return
        try {
            val capacity =
                (settings.parallelism - tasks.activeCount).coerceIn(0, settings.parallelism)
            if (capacity == 0) return
            val claimed = claim(owner, capacity)
            if (claimed is Result.Failed) {
                logger.warn("Chat push claim failed code={}", claimed.failure.code)
                return
            }
            for (item in (claimed as Result.Success).value) {
                if (!running) return
                val task = DeadlineTask {
                    try {
                        val result = deliver(item)
                        if (result is Result.Failed)
                            logger.warn("Chat push delivery failed code={}", result.failure.code)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } catch (_: CancellationException) {
                        /* The expired fenced lease is recoverable. */
                    } catch (error: Exception) {
                        logger.warn("Chat push delivery stopped category={}", error.javaClass.name)
                    }
                }
                try {
                    task.watch(
                        timers.schedule(
                            Runnable { task.cancel(true) },
                            Instant.now().plus(settings.deliveryTimeout),
                        )
                    )
                    tasks.execute(task)
                } catch (error: RuntimeException) {
                    task.cancel(false)
                    throw error
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: CancellationException) {
            /* Shutdown cancels a pending claim. */
        } catch (error: Exception) {
            if (running) logger.warn("Chat push poll stopped category={}", error.javaClass.name)
        }
    }

    @Synchronized
    override fun stop() {
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
}
