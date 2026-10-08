package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.*
import jakarta.servlet.*
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import java.util.concurrent.*
import org.slf4j.LoggerFactory

/** One response owns at most one pending read and one bounded buffer. */
internal class BinaryTransfer(
    private val context: AsyncContext,
    private val response: HttpServletResponse,
    range: ByteRange,
    private val blockSize: Int,
    private val timeout: Duration,
    private val executor: ThreadPoolExecutor,
    private val read: (Long, Int) -> Result<ByteArray>,
    private val correlationId: String,
    private val released: (BinaryTransfer) -> Unit,
) : WriteListener, AsyncListener {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val lock = Any()
    private val output = response.outputStream
    private val started = System.nanoTime()
    private var terminated = false
    private var offset = range.start
    private var remaining = range.length
    private var buffer: ByteArray? = null
    private var position = 0
    private var pending: FutureTask<Result<ByteArray>>? = null

    fun start() =
        synchronized(lock) {
            if (terminated) return@synchronized
            try {
                context.addListener(this)
                output.setWriteListener(this)
                pump()
            } catch (error: Exception) {
                transportFailed(error)
            }
        }

    override fun onWritePossible() =
        synchronized(lock) {
            if (!terminated)
                try {
                    pump()
                } catch (error: Exception) {
                    transportFailed(error)
                }
        }

    private fun pump() {
        if (terminated) return
        if (System.nanoTime() - started >= timeout.toNanos()) {
            stop(504, "download_timeout")
            return
        }
        while (buffer != null) {
            if (!output.isReady) return
            val bytes = requireNotNull(buffer)
            val size = minOf(16384, bytes.size - position)
            check(size > 0)
            output.write(bytes, position, size)
            position += size
            offset += size
            remaining -= size
            if (position == bytes.size) {
                buffer = null
                position = 0
            }
            if (System.nanoTime() - started >= timeout.toNanos()) {
                stop(504, "download_timeout")
                return
            }
        }
        if (remaining == 0L) {
            stop(null, null)
            return
        }
        if (pending != null) return
        val size = minOf(remaining, blockSize - offset % blockSize).toInt()
        val nextOffset = offset
        val task =
            object : FutureTask<Result<ByteArray>>(Callable { read(nextOffset, size) }) {
                override fun done() {
                    readFinished(this, size)
                }
            }
        pending = task
        try {
            executor.execute(task)
        } catch (error: RejectedExecutionException) {
            stop(503, "download_reader_busy")
        }
    }

    private fun readFinished(task: FutureTask<Result<ByteArray>>, expected: Int) {
        val result =
            try {
                task.get()
            } catch (error: CancellationException) {
                return
            } catch (error: Exception) {
                logger.warn(
                    "Download reader failed reference={} category={}",
                    correlationId,
                    error.cause?.javaClass?.name ?: error.javaClass.name,
                )
                Result.Failed(Failure(FailureKind.UNAVAILABLE, "download_read_failed"))
            }
        synchronized(lock) {
            if (terminated || pending !== task) return
            // Clear ownership before pumping; completion never cancels an unrelated reused thread.
            pending = null
            when (result) {
                is Result.Failed ->
                    stop(failureHttpStatus(result.failure.kind).value(), result.failure.code)
                is Result.Success -> {
                    if (result.value.size != expected) {
                        stop(500, "download_read_incomplete")
                        return
                    }
                    buffer = result.value
                    position = 0
                    try {
                        pump()
                    } catch (error: Exception) {
                        transportFailed(error)
                    }
                }
            }
        }
    }

    private fun transportFailed(error: Exception) {
        logger.debug(
            "Download connection ended reference={} category={}",
            correlationId,
            error.javaClass.name,
        )
        stop(500, "download_connection_closed")
    }

    fun stop(status: Int?, code: String?, complete: Boolean = true) =
        synchronized(lock) {
            if (terminated) return@synchronized
            terminated = true
            val task = pending
            pending = null
            buffer = null
            task?.cancel(true)
            if (task != null) executor.remove(task)
            if (code != null)
                logger.debug("Download stopped reference={} code={}", correlationId, code)
            try {
                if (status != null && !response.isCommitted) {
                    response.resetBuffer()
                    response.status = status
                    response.setContentLength(0)
                }
            } catch (error: Exception) {
                logger.debug(
                    "Download response already closed reference={} category={}",
                    correlationId,
                    error.javaClass.name,
                )
            } finally {
                released(this)
                if (complete)
                    try {
                        context.complete()
                    } catch (error: Exception) {
                        logger.debug(
                            "Download completion raced with closure reference={} category={}",
                            correlationId,
                            error.javaClass.name,
                        )
                    }
            }
        }

    override fun onError(error: Throwable) = stop(500, "download_connection_closed")

    override fun onTimeout(event: AsyncEvent) = stop(504, "download_timeout")

    override fun onError(event: AsyncEvent) = stop(500, "download_connection_closed")

    override fun onComplete(event: AsyncEvent) = stop(null, null, complete = false)

    override fun onStartAsync(event: AsyncEvent) = stop(500, "download_restarted")
}
