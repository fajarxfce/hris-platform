package dev.fajar.hris.worker.integrations

import dev.fajar.hris.administration.domain.usecases.PublishWorkerIntegrationObservation
import dev.fajar.hris.core.domain.Result
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledFuture
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/** One process identity and one fixed-delay timer; shutdown leaves an aging observation. */
class WorkerIntegrationReporter(
    private val publish: PublishWorkerIntegrationObservation,
    private val timer: ThreadPoolTaskScheduler,
    private val interval: Duration = Duration.ofSeconds(30),
) : SmartLifecycle {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val instanceId = UUID.randomUUID()
    @Volatile private var running = false
    private var started = false
    private var polling: ScheduledFuture<*>? = null

    init {
        require(interval.toMillis() in 10..30000)
    }

    @Synchronized
    override fun start() {
        check(!started) { "Worker integration reporter cannot be restarted" }
        started = true
        running = true
        try {
            polling = timer.scheduleWithFixedDelay(Runnable { report() }, interval)
        } catch (error: RuntimeException) {
            running = false
            throw error
        }
    }

    private fun report() {
        if (!running) return
        try {
            val result = publish.execute(instanceId)
            if (running && result is Result.Failed)
                logger.warn("Worker integration observation stopped code={}", result.failure.code)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: CancellationException) {
            /* Rollback leaves the next scheduled pass recoverable. */
        } catch (error: Exception) {
            if (running)
                logger.warn(
                    "Worker integration observation stopped category={}",
                    error.javaClass.name,
                )
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

    override fun getPhase() = Int.MAX_VALUE - 19
}
