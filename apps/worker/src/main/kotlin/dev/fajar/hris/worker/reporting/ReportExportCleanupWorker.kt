package dev.fajar.hris.worker.reporting

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.usecases.MaintainReportExports
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledFuture
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/** A single fixed-delay task with bounded database work and no per-session queue. */
class ReportExportCleanupWorker(
    private val maintain: MaintainReportExports,
    private val timer: ThreadPoolTaskScheduler,
    private val interval: Duration = Duration.ofMinutes(1),
) : SmartLifecycle {
    private val logger = LoggerFactory.getLogger(javaClass)
    @Volatile private var running = false
    private var started = false
    private var polling: ScheduledFuture<*>? = null

    init {
        require(interval in Duration.ofSeconds(1)..Duration.ofHours(1))
    }

    @Synchronized
    override fun start() {
        check(!started) { "Report export cleanup worker cannot be restarted" }
        started = true
        running = true
        try {
            polling = timer.scheduleWithFixedDelay(Runnable { poll() }, interval)
            logger.info("Report export cleanup started intervalSeconds={}", interval.seconds)
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
                logger.warn("Report export cleanup stopped code={}", result.failure.code)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: CancellationException) {
            /* The transaction rolls back; a later pass can acquire the same expired rows. */
        } catch (error: Exception) {
            if (running)
                logger.warn("Report export cleanup stopped category={}", error.javaClass.name)
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
