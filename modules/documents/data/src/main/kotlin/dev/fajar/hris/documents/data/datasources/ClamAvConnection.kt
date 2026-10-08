package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.documents.data.errors.DocumentScannerProtocolException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.time.Duration

/** Owns one nonblocking protocol connection. Every loop has an absolute time and size bound. */
class ClamAvConnection
private constructor(
    private val channel: SocketChannel,
    private val selector: Selector,
    private val settings: ClamAvSettings,
    private val started: Long,
) : AutoCloseable {
    private fun remaining(callStarted: Long, budget: Duration): Long {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val now = System.nanoTime()
        val remaining =
            minOf(
                settings.totalTimeout.toNanos() - (now - started),
                budget.toNanos() - (now - callStarted),
            )
        if (remaining <= 0) throw SocketTimeoutException("Document scanner deadline exceeded")
        return remaining
    }

    private fun awaitReady(operation: Int, callStarted: Long, budget: Duration) {
        val left = remaining(callStarted, budget)
        channel.keyFor(selector).interestOps(operation)
        selector.select(
            minOf(1000, maxOf(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(left)))
        )
        selector.selectedKeys().clear()
        remaining(callStarted, budget)
    }

    fun write(bytes: ByteBuffer) {
        val callStarted = System.nanoTime()
        while (bytes.hasRemaining()) {
            remaining(callStarted, settings.writeTimeout)
            if (channel.write(bytes) == 0)
                awaitReady(SelectionKey.OP_WRITE, callStarted, settings.writeTimeout)
        }
        remaining(callStarted, settings.writeTimeout)
    }

    fun readFrame(budget: Duration = settings.responseTimeout): String {
        val callStarted = System.nanoTime()
        val bytes = ByteArray(4096)
        val buffer = ByteBuffer.allocate(256)
        var count = 0
        while (count < bytes.size) {
            remaining(callStarted, budget)
            buffer.clear()
            val read = channel.read(buffer)
            if (read < 0) throw DocumentScannerProtocolException()
            if (read == 0) {
                awaitReady(SelectionKey.OP_READ, callStarted, budget)
                continue
            }
            buffer.flip()
            while (buffer.hasRemaining()) {
                val value = buffer.get()
                if (value.toInt() == 0) {
                    remaining(callStarted, budget)
                    return String(bytes, 0, count, Charsets.US_ASCII)
                }
                if (count >= bytes.size || value.toInt() !in 32..126)
                    throw DocumentScannerProtocolException()
                bytes[count++] = value
            }
        }
        throw DocumentScannerProtocolException()
    }

    override fun close() {
        try {
            channel.close()
        } finally {
            selector.close()
        }
    }

    companion object {
        fun connect(settings: ClamAvSettings, started: Long): ClamAvConnection {
            val selector = Selector.open()
            var channel: SocketChannel? = null
            try {
                channel = SocketChannel.open()
                channel.configureBlocking(false)
                channel.register(selector, 0)
                channel.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true)
                val connection = ClamAvConnection(channel, selector, settings, started)
                val callStarted = System.nanoTime()
                connection.remaining(callStarted, settings.connectTimeout)
                if (!channel.connect(settings.endpoint))
                    while (!channel.finishConnect()) connection.awaitReady(
                        SelectionKey.OP_CONNECT,
                        callStarted,
                        settings.connectTimeout,
                    )
                connection.remaining(callStarted, settings.connectTimeout)
                return connection
            } catch (failure: Throwable) {
                try {
                    channel?.close()
                } catch (closing: Throwable) {
                    failure.addSuppressed(closing)
                }
                try {
                    selector.close()
                } catch (closing: Throwable) {
                    failure.addSuppressed(closing)
                }
                throw failure
            }
        }
    }
}
