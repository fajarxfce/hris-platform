package dev.fajar.hris.worker.sync

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.sync.domain.usecases.MaintainMobileSync
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledFuture
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/** One bounded, fixed-delay maintenance pass; the database serializes competing publishers. */
class MobileSyncWorker(
    private val maintain: MaintainMobileSync,
    private val timer: ThreadPoolTaskScheduler,
    private val interval: Duration = Duration.ofSeconds(5),
) : SmartLifecycle {
    private val logger = LoggerFactory.getLogger(javaClass)
    @Volatile private var running = false
    private var started = false
    private var polling: ScheduledFuture<*>? = null

    init {
        require(interval.toMillis() in 10..60000)
    }

    @Synchronized
    override fun start() {
        check(!started) { "Sync worker cannot be restarted" }
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
            val result = maintain.execute()
            if (running && result is Result.Failed)
                logger.warn("Sync maintenance stopped code={}", result.failure.code)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: CancellationException) {
            /* Transaction rollback leaves the next pass recoverable. */
        } catch (error: Exception) {
            if (running) logger.warn("Sync maintenance stopped category={}", error.javaClass.name)
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
