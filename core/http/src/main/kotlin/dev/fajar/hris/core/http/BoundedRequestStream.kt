package dev.fajar.hris.core.http

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream

/** Counts actual streamed bytes, including requests without Content-Length. */
class BoundedRequestStream(
    private val source: ServletInputStream,
    private val maximum: Long,
    private val budget: java.time.Duration = java.time.Duration.ofSeconds(30),
    private val nanoTime: () -> Long = System::nanoTime,
) : ServletInputStream() {
    private var consumed = 0L
    private val started = nanoTime()

    init {
        require(maximum >= 0)
        require(!budget.isZero && !budget.isNegative && budget <= java.time.Duration.ofSeconds(30))
    }

    override fun read(): Int {
        if (nanoTime() - started >= budget.toNanos()) throw RequestBodyTimeoutException()
        val value = source.read()
        if (nanoTime() - started >= budget.toNanos()) throw RequestBodyTimeoutException()
        if (value >= 0 && ++consumed > maximum) throw RequestBodyTooLargeException()
        return value
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.size)
        if (length == 0) return 0
        val allowed = minOf(length.toLong(), maximum - consumed + 1).coerceAtLeast(1).toInt()
        if (nanoTime() - started >= budget.toNanos()) throw RequestBodyTimeoutException()
        val count = source.read(bytes, offset, allowed)
        if (nanoTime() - started >= budget.toNanos()) throw RequestBodyTimeoutException()
        if (count > 0) {
            consumed += count
            if (consumed > maximum) throw RequestBodyTooLargeException()
        }
        return count
    }

    override fun isFinished(): Boolean = source.isFinished

    override fun isReady(): Boolean = source.isReady

    override fun setReadListener(listener: ReadListener) = source.setReadListener(listener)

    override fun close() = source.close()
}
