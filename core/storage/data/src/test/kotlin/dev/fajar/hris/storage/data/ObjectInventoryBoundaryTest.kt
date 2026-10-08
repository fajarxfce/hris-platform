package dev.fajar.hris.storage.data

import com.sun.net.httpserver.HttpServer
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.data.datasources.*
import dev.fajar.hris.storage.data.di.*
import dev.fajar.hris.storage.data.errors.InvalidObjectStorageResponse
import dev.fajar.hris.storage.data.models.*
import dev.fajar.hris.storage.data.repositories.PrivateObjectStorageRepository
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import software.amazon.awssdk.http.Abortable

class ObjectInventoryBoundaryTest {
    private class Probe(var page: ObjectInventoryPageData) : ObjectStorageDataSource {
        var calls = 0
        var afterRead: (() -> Unit)? = null

        override fun list(prefix: String, afterKey: String?, limit: Int): ObjectInventoryPageData {
            calls++
            afterRead?.invoke()
            return page
        }

        override fun put(key: String, bytes: ByteArray, sha256: String): ObjectMetadataData =
            throw UnsupportedOperationException()

        override fun metadata(key: String): ObjectMetadataData =
            throw UnsupportedOperationException()

        override fun read(key: String, offset: Long, length: Int, etag: String): ByteArray =
            throw UnsupportedOperationException()

        override fun delete(key: String): Unit = throw UnsupportedOperationException()

        override fun close() = Unit
    }

    @Test
    fun inventoryRejectsInvalidScopeOversizedAndNonprogressingProviderPages() {
        val company = UUID.randomUUID()
        val entry = ObjectInventoryEntryData("$company/a", 1, "etag", Instant.EPOCH)
        val probe = Probe(ObjectInventoryPageData(listOf(entry), false))
        val repo = PrivateObjectStorageRepository(probe)
        assertTrue(repo.list(company, null, 1) is Result.Success)
        assertEquals(
            FailureKind.VALIDATION,
            (repo.list(company, "${UUID.randomUUID()}/a", 1) as Result.Failed).failure.kind,
        )
        assertEquals(
            FailureKind.VALIDATION,
            (repo.list(company, null, 101) as Result.Failed).failure.kind,
        )
        assertEquals(1, probe.calls)
        for (page in
            listOf(
                ObjectInventoryPageData(listOf(entry, entry.copy(key = "$company/b")), false),
                ObjectInventoryPageData(emptyList(), true),
                ObjectInventoryPageData(listOf(entry.copy(key = "${UUID.randomUUID()}/a")), false),
                ObjectInventoryPageData(listOf(entry.copy(size = -1)), false),
            )) {
            probe.page = page
            assertEquals(
                "invalid_storage_response",
                (repo.list(company, null, 1) as Result.Failed).failure.code,
            )
        }
        probe.page = ObjectInventoryPageData(listOf(entry), false)
        assertEquals(
            "invalid_storage_response",
            (repo.list(company, entry.key, 1) as Result.Failed).failure.code,
        )
        probe.page = ObjectInventoryPageData(listOf(entry.copy(key = "$company/b"), entry), false)
        assertEquals(
            "invalid_storage_response",
            (repo.list(company, null, 2) as Result.Failed).failure.code,
        )
        probe.page = ObjectInventoryPageData(listOf(entry, entry), false)
        assertEquals(
            "invalid_storage_response",
            (repo.list(company, null, 2) as Result.Failed).failure.code,
        )
        probe.page =
            ObjectInventoryPageData(
                listOf(
                    entry.copy(key = "$company/", size = 0),
                    entry.copy(key = "$company/"),
                    entry.copy(key = "$company/😀"),
                ),
                false,
            )
        assertTrue(repo.list(company, null, 3) is Result.Success)
        probe.page = ObjectInventoryPageData(listOf(entry), false)
        probe.afterRead = { Thread.currentThread().interrupt() }
        try {
            assertThrows(InterruptedException::class.java) { repo.list(company, null, 1) }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun declaredChunkedAndEncodedOversizedXmlCannotReachAnUnboundedParser() {
        val mode = AtomicInteger()
        val calls = AtomicInteger()
        val pool = Executors.newSingleThreadExecutor()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = pool
        server.createContext("/") { exchange ->
            exchange.use {
                calls.incrementAndGet()
                val body =
                    ("<ListBucketResult><Name>" +
                            "x".repeat(524289) +
                            "</Name><IsTruncated>false</IsTruncated></ListBucketResult>")
                        .toByteArray()
                if (mode.get() == 2) exchange.responseHeaders.add("Content-Encoding", "gzip")
                exchange.responseHeaders.add("Content-Type", "application/xml")
                try {
                    exchange.sendResponseHeaders(
                        200,
                        if (mode.get() == 0) body.size.toLong() else 0,
                    )
                    exchange.responseBody.write(body)
                } catch (ignored: java.io.IOException) {}
            }
        }
        server.start()
        val settings =
            ObjectStorageSettings(
                URI("http://127.0.0.1:${server.address.port}"),
                "garage",
                "hris-tests",
                "fixture",
                "fixture",
                true,
                Duration.ofMillis(200),
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
            )
        try {
            S3ObjectStorageDataSource(createObjectStorageClient(settings), "hris-tests").use {
                source ->
                val repo = PrivateObjectStorageRepository(source)
                for (index in 0..2) {
                    mode.set(index)
                    val start = System.nanoTime()
                    val result = repo.list(UUID.randomUUID(), null, 100)
                    assertEquals("invalid_storage_response", (result as Result.Failed).failure.code)
                    assertTrue(Duration.ofNanos(System.nanoTime() - start) < Duration.ofSeconds(2))
                    assertEquals(index + 1, calls.get())
                }
            }
        } finally {
            server.stop(0)
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun tricklingListingXmlTerminatesAtTheSdkDeadline() {
        val pool = Executors.newSingleThreadExecutor()
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = pool
        server.createContext("/") { exchange ->
            exchange.use {
                calls.incrementAndGet()
                exchange.responseHeaders.add("Content-Type", "application/xml")
                exchange.sendResponseHeaders(200, 0)
                try {
                    exchange.responseBody.write("<ListBucketResult>".toByteArray())
                    exchange.responseBody.flush()
                    repeat(60) {
                        exchange.responseBody.write(' '.code)
                        exchange.responseBody.flush()
                        TimeUnit.MILLISECONDS.sleep(100)
                    }
                } catch (ignored: java.io.IOException) {} catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        server.start()
        val settings =
            ObjectStorageSettings(
                URI("http://127.0.0.1:${server.address.port}"),
                "garage",
                "hris-tests",
                "fixture",
                "fixture",
                true,
                Duration.ofMillis(200),
                Duration.ofMillis(300),
                Duration.ofMillis(500),
            )
        try {
            S3ObjectStorageDataSource(createObjectStorageClient(settings), "hris-tests").use {
                source ->
                val start = System.nanoTime()
                val result =
                    PrivateObjectStorageRepository(source).list(UUID.randomUUID(), null, 100)
                assertTrue(result is Result.Failed)
                assertTrue(Duration.ofNanos(System.nanoTime() - start) < Duration.ofSeconds(2))
                assertEquals(1, calls.get())
            }
        } finally {
            server.stop(0)
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun cancellationDuringListingDiscardsTheResponseAndReleasesTheClient() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val issue = AtomicReference<Throwable?>()
        val calls = AtomicInteger()
        val servers = Executors.newSingleThreadExecutor()
        val callers = Executors.newSingleThreadExecutor()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = servers
        server.createContext("/") { exchange ->
            exchange.use {
                val n = calls.incrementAndGet()
                try {
                    exchange.responseHeaders.add("Content-Type", "application/xml")
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.write("<ListBucketResult>".toByteArray())
                    exchange.responseBody.flush()
                    if (n == 1) {
                        entered.countDown()
                        release.await(3, TimeUnit.SECONDS)
                    }
                    exchange.responseBody.write(
                        "<IsTruncated>false</IsTruncated></ListBucketResult>".toByteArray()
                    )
                } catch (ignored: java.io.IOException) {}
            }
        }
        server.start()
        val settings =
            ObjectStorageSettings(
                URI("http://127.0.0.1:${server.address.port}"),
                "garage",
                "hris-tests",
                "fixture",
                "fixture",
                true,
                Duration.ofMillis(200),
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
            )
        try {
            S3ObjectStorageDataSource(createObjectStorageClient(settings), "hris-tests").use {
                source ->
                val repo = PrivateObjectStorageRepository(source)
                val pending =
                    callers.submit {
                        try {
                            repo.list(UUID.randomUUID(), null, 100)
                        } catch (error: Exception) {
                            issue.set(error)
                        } finally {
                            finished.countDown()
                        }
                    }
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                pending.cancel(true)
                assertTrue(finished.await(2, TimeUnit.SECONDS))
                assertTrue(
                    issue.get() is InterruptedException || issue.get() is CancellationException,
                    issue.get()?.javaClass?.name,
                )
                release.countDown()
                val retry = repo.list(UUID.randomUUID(), null, 100)
                assertTrue(retry is Result.Success, retry.toString())
                assertEquals(2, calls.get())
            }
        } finally {
            release.countDown()
            server.stop(0)
            servers.shutdownNow()
            callers.shutdownNow()
            assertTrue(servers.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun xmlStreamLimitsCannotBeBypassedBySkipAndAlwaysAbortUnreadBytes() {
        val aborted = AtomicInteger()
        val bytes = ByteArrayInputStream(ByteArray(100))
        BoundedInventoryInputStream(bytes, Abortable { aborted.incrementAndGet() }, 10).use { stream
            ->
            assertEquals(8L, stream.skip(8))
            assertThrows(InvalidObjectStorageResponse::class.java) { stream.read(ByteArray(4)) }
            assertEquals(1, aborted.get())
            assertTrue(bytes.available() > 80)
        }
        assertEquals(1, aborted.get())
        val complete = AtomicInteger()
        BoundedInventoryInputStream(
                ByteArrayInputStream(ByteArray(10)),
                Abortable { complete.incrementAndGet() },
                10,
            )
            .use { stream -> assertEquals(10, stream.readAllBytes().size) }
        assertEquals(1, complete.get())
    }
}
