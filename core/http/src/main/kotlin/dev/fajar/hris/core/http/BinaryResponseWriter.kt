package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.*
import org.slf4j.LoggerFactory

/** Owns bounded transport resources; reads run separately from nonblocking servlet output. */
class BinaryResponseWriter(private val settings: BinaryResponseSettings) : AutoCloseable {
    private val lifecycle = Any()
    private var closed = false
    private val permits = Semaphore(settings.concurrency)
    private val active = ConcurrentHashMap.newKeySet<BinaryTransfer>()
    private val readers =
        ThreadPoolExecutor(
                settings.concurrency,
                settings.concurrency,
                30,
                TimeUnit.SECONDS,
                ArrayBlockingQueue(settings.concurrency),
                Thread.ofPlatform().name("hris-download-", 0).factory(),
                ThreadPoolExecutor.AbortPolicy(),
            )
            .apply { allowCoreThreadTimeOut(true) }

    fun write(
        request: HttpServletRequest,
        response: HttpServletResponse,
        range: ByteRange,
        blockSize: Int,
        read: (Long, Int) -> Result<ByteArray>,
    ) {
        require(
            range.start >= 0 &&
                range.end >= range.start &&
                range.end < 104857600 &&
                blockSize in 1..1048576
        )
        val transfer =
            synchronized(lifecycle) {
                if (closed || !permits.tryAcquire()) {
                    response.status = if (closed) 503 else 429
                    response.setContentLength(0)
                    response.setHeader("Retry-After", "1")
                    return
                }
                var context: jakarta.servlet.AsyncContext? = null
                try {
                    context = request.startAsync()
                    context.timeout = settings.timeout.toMillis()
                    BinaryTransfer(
                            context,
                            response,
                            range,
                            blockSize,
                            settings.timeout,
                            readers,
                            read,
                            request.getAttribute("hris.correlationId")?.toString() ?: "unknown",
                        ) { finished ->
                            active.remove(finished)
                            permits.release()
                        }
                        .also { active.add(it) }
                } catch (error: Exception) {
                    permits.release()
                    try {
                        context?.complete()
                    } catch (closing: Exception) {
                        error.addSuppressed(closing)
                    }
                    throw error
                }
            }
        // Never hold the registry lock while acquiring a transfer lock.
        transfer.start()
    }

    override fun close() {
        val closing =
            synchronized(lifecycle) {
                if (closed) return
                closed = true
                active.toList()
            }
        closing.forEach { it.stop(503, "download_shutdown") }
        readers.shutdownNow()
        try {
            if (
                !readers.awaitTermination(
                    settings.shutdownTimeout.toMillis(),
                    TimeUnit.MILLISECONDS,
                )
            )
                LoggerFactory.getLogger(javaClass)
                    .warn("Download reader shutdown reached its time limit")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
