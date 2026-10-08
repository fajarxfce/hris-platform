package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.http.*
import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.*

@Import(BinaryTransportProbeConfiguration::class, BinaryTransportProbeController::class)
class BinaryTransportHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var probe: BinaryTransportProbeController

    @AfterEach
    fun clearTransfers() {
        probe.sources.clear()
    }

    private fun open(browser: HttpClient, id: UUID): Socket {
        val socket = Socket()
        socket.receiveBufferSize = 1024
        socket.soTimeout = 3000
        try {
            socket.connect(InetSocketAddress("127.0.0.1", port), 2000)
            val cookies =
                (browser.cookieHandler().orElseThrow() as CookieManager)
                    .cookieStore
                    .cookies
                    .joinToString("; ") { "${it.name}=${it.value}" }
            socket
                .getOutputStream()
                .write(
                    "GET /api/v1/test/binary/$id HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nCookie: $cookies\r\nConnection: close\r\n\r\n"
                        .toByteArray()
                )
            socket.getOutputStream().flush()
            return socket
        } catch (error: Exception) {
            socket.close()
            throw error
        }
    }

    private fun fetch(browser: HttpClient, id: UUID) =
        browser.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/v1/test/binary/$id"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )

    @Test
    fun slowClientsStopReadingAtBackpressureAndTimeOutWithoutBlockingTheReadPool() {
        val browser = client()
        login(browser)
        val first = BinaryTransportSource(104857600)
        val second = BinaryTransportSource(104857600)
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        probe.sources[firstId] = first
        probe.sources[secondId] = second
        open(browser, firstId).use { one ->
            open(browser, secondId).use { two ->
                assertTrue(one.isConnected && two.isConnected)
                assertTrue(first.entered.await(3, TimeUnit.SECONDS))
                assertTrue(second.entered.await(3, TimeUnit.SECONDS))
                val quickId = UUID.randomUUID()
                probe.sources[quickId] = BinaryTransportSource(16)
                assertEquals(429, fetch(browser, quickId).statusCode())
                assertTrue(first.completed.await(5, TimeUnit.SECONDS))
                assertTrue(second.completed.await(5, TimeUnit.SECONDS))
                assertTrue(first.calls.get() in 1..99)
                assertTrue(second.calls.get() in 1..99)
                assertEquals(200, fetch(browser, quickId).statusCode())
            }
        }
    }

    @Test
    fun disconnectAndTimeoutCancelPendingReadsAndReturnAdmissionSlots() {
        val browser = client()
        login(browser)
        val stopped = CountDownLatch(2)
        val sources =
            (1..2).map {
                val id = UUID.randomUUID()
                val source = BinaryTransportSource(104857600)
                source.beforeRead = {
                    try {
                        check(CountDownLatch(1).await(10, TimeUnit.SECONDS))
                    } finally {
                        stopped.countDown()
                    }
                }
                probe.sources[id] = source
                id to source
            }
        val sockets = sources.map { open(browser, it.first) }
        try {
            sources.forEach { assertTrue(it.second.entered.await(3, TimeUnit.SECONDS)) }
        } finally {
            sockets.forEach { it.close() }
        }
        assertTrue(stopped.await(5, TimeUnit.SECONDS))
        sources.forEach { assertTrue(it.second.completed.await(5, TimeUnit.SECONDS)) }
        val id = UUID.randomUUID()
        probe.sources[id] = BinaryTransportSource(16)
        val response = fetch(browser, id)
        assertEquals(200, response.statusCode())
        assertEquals(16, response.body().size)
    }
}

class BinaryTransportSource(val size: Int) {
    val entered = CountDownLatch(1)
    val completed = CountDownLatch(1)
    val calls = AtomicInteger()
    @Volatile var beforeRead: (() -> Unit)? = null
}

@org.springframework.boot.test.context.TestComponent
@RestController
class BinaryTransportProbeController(private val writer: BinaryResponseWriter) {
    val sources = ConcurrentHashMap<UUID, BinaryTransportSource>()

    @GetMapping("/api/v1/test/binary/{id}")
    fun serve(@PathVariable id: UUID, request: HttpServletRequest, response: HttpServletResponse) {
        val source = sources.getValue(id)
        response.contentType = "application/octet-stream"
        response.setContentLength(source.size)
        writer.write(request, response, ByteRange(0, source.size - 1L), 1048576) { _, size ->
            source.calls.incrementAndGet()
            source.entered.countDown()
            source.beforeRead?.invoke()
            Result.Success(ByteArray(size) { 16 })
        }
        if (request.isAsyncStarted)
            request.asyncContext.addListener(
                object : AsyncListener {
                    override fun onComplete(event: AsyncEvent) {
                        source.completed.countDown()
                    }

                    override fun onTimeout(event: AsyncEvent) {}

                    override fun onError(event: AsyncEvent) {}

                    override fun onStartAsync(event: AsyncEvent) {}
                }
            )
    }
}

@TestConfiguration(proxyBeanMethods = false)
class BinaryTransportProbeConfiguration {
    @Bean(destroyMethod = "close")
    @Primary
    fun shortBinaryWriter() =
        BinaryResponseWriter(
            BinaryResponseSettings(concurrency = 2, timeout = Duration.ofMillis(900))
        )
}
