package dev.fajar.hris.worker

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*

class SmtpFixture(
    private val recipientCode: Int = 250,
    private val stall: Boolean = false,
    private val finalCode: Int = 250,
) : AutoCloseable {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val release = CountDownLatch(1)
    private val executor =
        ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            Thread.ofPlatform().name("hris-smtp-fixture").factory(),
        )
    private var stopped = false
    private var connection: Socket? = null
    val message = CompletableFuture<String>()
    val delivered = java.util.concurrent.atomic.AtomicInteger()
    val connected = CountDownLatch(1)
    val port: Int = server.localPort
    private val task =
        executor.submit {
            try {
                val socket = server.accept()
                synchronized(this) { if (stopped) socket.close() else connection = socket }
                socket.use {
                    if (socket.isClosed) return@submit
                    socket.soTimeout = 3000
                    connected.countDown()
                    if (stall) {
                        check(release.await(5, TimeUnit.SECONDS))
                        return@submit
                    }
                    val output = socket.getOutputStream()
                    fun reply(value: String) {
                        output.write((value + "\r\n").toByteArray(Charsets.US_ASCII))
                        output.flush()
                    }
                    val input = socket.getInputStream()
                    fun line(): String? {
                        val bytes = java.io.ByteArrayOutputStream()
                        repeat(4096) {
                            val byte = input.read()
                            if (byte < 0)
                                return if (bytes.size() == 0) null else throw java.io.EOFException()
                            if (byte == 10)
                                return bytes.toString(Charsets.US_ASCII).removeSuffix("\r")
                            bytes.write(byte)
                        }
                        throw java.io.IOException("Fixture line limit")
                    }
                    reply("220 fixture ESMTP")
                    var readingData = false
                    val data = StringBuilder()
                    repeat(2048) {
                        val command = line() ?: return@submit
                        if (readingData) {
                            if (command == ".") {
                                readingData = false
                                if (finalCode == 250) delivered.incrementAndGet()
                                message.complete(data.toString())
                                reply("$finalCode message response")
                            } else {
                                require(data.length + command.length < 131072)
                                data.append(command).append("\r\n")
                            }
                        } else
                            when {
                                command.startsWith("EHLO") || command.startsWith("HELO") ->
                                    reply("250 fixture")
                                command.startsWith("MAIL FROM") -> reply("250 sender accepted")
                                command.startsWith("RCPT TO") ->
                                    reply("$recipientCode recipient response")
                                command == "DATA" -> {
                                    readingData = true
                                    reply("354 continue")
                                }
                                command == "RSET" -> reply("250 reset")
                                command == "QUIT" -> {
                                    reply("221 bye")
                                    return@submit
                                }
                                else -> reply("500 unsupported")
                            }
                    }
                    throw java.io.IOException("Fixture command limit")
                }
            } catch (error: Exception) {
                synchronized(this) { if (!stopped) message.completeExceptionally(error) }
            }
        }

    fun awaitConnectionClosed() {
        task.get(3, TimeUnit.SECONDS)
    }

    override fun close() {
        synchronized(this) {
            stopped = true
            server.close()
            connection?.close()
        }
        release.countDown()
        executor.shutdownNow()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        task.get(5, TimeUnit.SECONDS)
    }
}
