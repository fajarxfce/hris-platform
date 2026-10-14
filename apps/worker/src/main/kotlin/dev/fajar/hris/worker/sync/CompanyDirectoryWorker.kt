package dev.fajar.hris.worker.sync

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.domain.usecases.RefreshCompanyDirectory
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledFuture
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/** One owned timer; projection transactions lock bounded due rows and skip competing workers. */
class CompanyDirectoryWorker(
    private val maintain: RefreshCompanyDirectory,
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
        check(!started) { "Directory worker cannot be restarted" }
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
                logger.warn("Directory projection stopped code={}", result.failure.code)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: CancellationException) {
            /* Transaction rollback leaves the next pass recoverable. */
        } catch (error: Exception) {
            if (running)
                logger.warn("Directory projection stopped category={}", error.javaClass.name)
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
