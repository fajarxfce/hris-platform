package dev.fajar.hris.documents.data

import java.io.DataInputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.*

class ClamAvFixture : AutoCloseable {
    private val listener = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port
        get() = listener.localPort

    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val pool =
        ThreadPoolExecutor(
            2,
            2,
            0,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(4),
            ThreadFactory { r -> Thread(r, "clamav-fixture-client") },
        )
    @Volatile
    var stream: (DataInputStream, OutputStream) -> Unit = { input, output ->
        consume(input)
        output.write("stream: OK\u0000".toByteArray())
        output.flush()
    }
    private val acceptor =
        Thread(
                {
                    while (!listener.isClosed) {
                        val socket =
                            try {
                                listener.accept()
                            } catch (e: java.io.IOException) {
                                break
                            }
                        clients += socket
                        try {
                            pool.execute {
                                try {
                                    socket.use { s ->
                                        s.soTimeout = 5000
                                        val input = DataInputStream(s.getInputStream())
                                        val command = StringBuilder()
                                        while (command.length < 16) {
                                            val value = input.read()
                                            if (value < 0) throw java.io.EOFException()
                                            if (value == 0) break
                                            command.append(value.toChar())
                                        }
                                        when (command.toString()) {
                                            "zVERSION" -> {
                                                s.getOutputStream()
                                                    .write("ClamAV fixture/1\u0000".toByteArray())
                                                s.getOutputStream().flush()
                                            }
                                            "zINSTREAM" -> stream(input, s.getOutputStream())
                                            else ->
                                                throw java.io.IOException("Invalid fixture command")
                                        }
                                    }
                                } catch (ignored: java.io.IOException) {} catch (
                                    ignored: InterruptedException) {
                                    Thread.currentThread().interrupt()
                                } finally {
                                    clients -= socket
                                }
                            }
                        } catch (ignored: RejectedExecutionException) {
                            clients -= socket
                            socket.close()
                        }
                    }
                },
                "clamav-fixture-accept",
            )
            .also { it.start() }

    fun consume(input: DataInputStream) {
        var count = 0
        while (count <= 104857600) {
            val size = input.readInt()
            check(size in 0..1048576)
            if (size == 0) return
            check(input.readNBytes(size).size == size)
            count += size
        }
        error("Fixture size limit")
    }

    override fun close() {
        listener.close()
        clients.forEach { it.close() }
        pool.shutdownNow()
        acceptor.join(5000)
        check(!acceptor.isAlive)
        check(pool.awaitTermination(5, TimeUnit.SECONDS))
    }
}
