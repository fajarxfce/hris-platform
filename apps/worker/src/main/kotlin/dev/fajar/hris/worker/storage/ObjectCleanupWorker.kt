package dev.fajar.hris.worker.storage

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.storage.domain.usecases.CollectObjectGarbage
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledFuture
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/** One lifecycle-owned poller with a bounded batch and no queued per-object tasks. */
class ObjectCleanupWorker(
    private val collect: CollectObjectGarbage,
    private val timer: ThreadPoolTaskScheduler,
    private val interval: Duration = Duration.ofSeconds(5),
) : SmartLifecycle {
    private val owner = UUID.randomUUID()
    private val logger = LoggerFactory.getLogger(javaClass)
    @Volatile private var running = false
    private var started = false
    private var polling: ScheduledFuture<*>? = null

    init {
        require(interval.toMillis() in 10..60000)
    }

    @Synchronized
    override fun start() {
        check(!started) { "Cleanup worker cannot be restarted" }
        started = true
        running = true
        try {
            polling = timer.scheduleWithFixedDelay(Runnable { poll() }, interval)
        } catch (error: RuntimeException) {
            running = false
            throw error
        }
    }

    private fun poll() {
        if (!running) return
        try {
            val result = collect.execute(owner, 2)
            if (result is Result.Failed)
                logger.warn("Object cleanup stopped code={}", result.failure.code)
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (ignored: CancellationException) {
            /* The expired lease remains recoverable. */
        } catch (error: Exception) {
            if (running) logger.warn("Object cleanup stopped category={}", error.javaClass.name)
        }
    }

    @Synchronized
    override fun stop() {
        running = false
        polling?.cancel(true)
        polling = null
        timer.shutdown()
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
