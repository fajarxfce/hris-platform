package dev.fajar.hris.core.http

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream

/** Counts actual streamed bytes, including requests without Content-Length. */
class BoundedRequestStream(private val source: ServletInputStream, private val maximum: Long) :
    ServletInputStream() {
    private var consumed = 0L

    init {
        require(maximum >= 0)
    }

    override fun read(): Int {
        val value = source.read()
        if (value >= 0 && ++consumed > maximum) throw RequestBodyTooLargeException()
        return value
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.size)
        if (length == 0) return 0
        val allowed = minOf(length.toLong(), maximum - consumed + 1).coerceAtLeast(1).toInt()
        val count = source.read(bytes, offset, allowed)
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
