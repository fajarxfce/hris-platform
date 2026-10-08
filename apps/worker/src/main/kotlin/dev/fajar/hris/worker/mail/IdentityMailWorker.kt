package dev.fajar.hris.worker.mail

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.usecases.DeliverIdentityMail
import dev.fajar.hris.identity.domain.usecases.LeaseIdentityMail
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledFuture
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/** Owns a dedicated executor and scheduler; no queued or retained per-delivery registry. */
class IdentityMailWorker(
    private val lease: LeaseIdentityMail,
    private val deliver: DeliverIdentityMail,
    private val tasks: ThreadPoolTaskExecutor,
    private val timers: ThreadPoolTaskScheduler,
    private val settings: IdentityMailWorkerSettings = IdentityMailWorkerSettings(),
) : SmartLifecycle {
    private val owner = UUID.randomUUID()
    private val logger = LoggerFactory.getLogger(javaClass)
    @Volatile private var running = false
    private var started = false
    private var polling: ScheduledFuture<*>? = null

    @Synchronized
    override fun start() {
        check(!started) { "Mail worker cannot be restarted" }
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
            val capacity = (2 - tasks.activeCount).coerceIn(0, 2)
            if (capacity == 0) return
            val claimed = lease.execute(owner, capacity)
            if (claimed is Result.Failed) {
                logger.warn("Mail claim failed code={}", claimed.failure.code)
                return
            }
            for (item in (claimed as Result.Success).value) {
                if (!running) return
                val task = TimedMailDelivery {
                    try {
                        val result = deliver.execute(item)
                        if (result is Result.Failed)
                            logger.warn("Mail delivery failed code={}", result.failure.code)
                    } catch (error: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } catch (_: CancellationException) {
                        /* The fenced lease is recoverable after expiry. */
                    } catch (error: Exception) {
                        logger.warn("Mail delivery stopped category={}", error.javaClass.name)
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
            /* Shutdown cancels the current claim. */
        } catch (error: Exception) {
            if (running) logger.warn("Mail poll stopped category={}", error.javaClass.name)
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
