package dev.fajar.hris.storage.data

import com.sun.net.httpserver.HttpServer
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.data.datasources.*
import dev.fajar.hris.storage.data.di.*
import dev.fajar.hris.storage.data.errors.*
import dev.fajar.hris.storage.data.repositories.PrivateObjectStorageRepository
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ObjectStorageBoundaryTest {
    @Test
    fun boundariesPreserveNestedCancellationAndReleaseCapacityOnEveryExit() {
        assertThrows(CancellationException::class.java) {
            safeObjectStorageCall<Unit> {
                throw IllegalStateException("suppressed", CancellationException())
            }
        }
        assertThrows(InterruptedException::class.java) {
            safeObjectStorageCall<Unit> {
                throw IllegalStateException("suppressed", InterruptedException())
            }
        }
        val permits = Semaphore(1)
        assertThrows(IllegalStateException::class.java) {
            withObjectStorageCapacity<Unit>(permits) { throw IllegalStateException() }
        }
        assertEquals(1, permits.availablePermits())
        assertEquals(
            FailureKind.UNAVAILABLE,
            (safeObjectStorageCall { withObjectStorageCapacity(Semaphore(0)) { 1 } }
                    as Result.Failed)
                .failure
                .kind,
        )
        try {
            assertThrows(InterruptedException::class.java) {
                safeObjectStorageCall {
                    withObjectStorageCapacity(permits) {
                        Thread.currentThread().interrupt()
                        1
                    }
                }
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(1, permits.availablePermits())
        val failed =
            safeObjectStorageCall<Unit> {
                throw UnsupportedOperationException("private-key-secret")
            }
        assertFalse(failed.toString().contains("private-key-secret"))
        assertEquals(
            "object_storage_not_configured",
            (PrivateObjectStorageRepository(UnavailableObjectStorageDataSource())
                    .metadata("valid-key") as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun settingsRejectUnexpectedDestinationsWithoutIncludingSecretsInTheirRepresentation() {
        val base =
            ObjectStorageSettings(
                URI("https://storage.example.test"),
                "garage",
                "hris-bucket",
                "key",
                "secret",
            )
        assertFalse(base.toString().contains("secret"))
        for (uri in
            listOf(
                "http://storage.example.test",
                "https://user:password@storage.example.test",
                "https://storage.example.test/other",
                "https://storage.example.test?q=secret",
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                ObjectStorageSettings(URI(uri), "garage", "hris-bucket", "key", "secret")
            }
        }
    }

    @Test
    fun providerErrorsAreNotRetriedAndStalledReadsHaveABoundedDeadline() {
        val calls = AtomicInteger()
        val mode = AtomicInteger()
        val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = pool
        server.createContext("/") { exchange ->
            exchange.use {
                calls.incrementAndGet()
                if (mode.get() == 1) release.await(3, TimeUnit.SECONDS)
                try {
                    exchange.sendResponseHeaders(503, -1)
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
                Duration.ofMillis(200),
                Duration.ofMillis(500),
            )
        try {
            S3ObjectStorageDataSource(createObjectStorageClient(settings), "hris-tests").use {
                source ->
                val repo = PrivateObjectStorageRepository(source)
                assertEquals(
                    FailureKind.UNAVAILABLE,
                    (repo.metadata("fixture-key") as Result.Failed).failure.kind,
                )
                assertEquals(1, calls.get())
                mode.set(1)
                val start = System.nanoTime()
                assertEquals(
                    FailureKind.UNAVAILABLE,
                    (repo.metadata("fixture-key") as Result.Failed).failure.kind,
                )
                assertTrue(Duration.ofNanos(System.nanoTime() - start) < Duration.ofSeconds(2))
                assertEquals(2, calls.get())
            }
        } finally {
            release.countDown()
            server.stop(0)
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun cancelledSdkReadsTerminateAndReleaseTheirCapacity() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val issue = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val serverPool = Executors.newSingleThreadExecutor()
        val callers = Executors.newSingleThreadExecutor()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = serverPool
        server.createContext("/") { exchange ->
            exchange.use {
                entered.countDown()
                release.await(3, TimeUnit.SECONDS)
                try {
                    exchange.sendResponseHeaders(503, -1)
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
                val task =
                    callers.submit {
                        try {
                            repo.metadata("fixture-key")
                        } catch (error: Exception) {
                            issue.set(error)
                        } finally {
                            finished.countDown()
                        }
                    }
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                task.cancel(true)
                assertTrue(finished.await(2, TimeUnit.SECONDS))
                assertTrue(
                    issue.get() is InterruptedException || issue.get() is CancellationException,
                    issue.get()?.javaClass?.name ?: "Cancellation did not propagate",
                )
                release.countDown()
                assertEquals(
                    "object_storage_unavailable",
                    (repo.metadata("fixture-key") as Result.Failed).failure.code,
                )
            }
        } finally {
            release.countDown()
            server.stop(0)
            serverPool.shutdownNow()
            callers.shutdownNow()
            assertTrue(serverPool.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun aTricklingResponseCannotKeepAReadAliveBeyondItsBudget() {
        val pool = Executors.newSingleThreadExecutor()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = pool
        server.createContext("/") { exchange ->
            exchange.use {
                exchange.responseHeaders.add("ETag", "\"fixture\"")
                exchange.responseHeaders.add("Content-Range", "bytes 0-63/64")
                exchange.sendResponseHeaders(206, 64)
                try {
                    for (index in 0 until 64) {
                        exchange.responseBody.write(index)
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
            S3ObjectStorageDataSource(
                    createObjectStorageClient(settings),
                    "hris-tests",
                    settings.callTimeout,
                )
                .use { source ->
                    val started = System.nanoTime()
                    val result =
                        PrivateObjectStorageRepository(source)
                            .read("fixture-key", 0, 64, "\"fixture\"")
                    assertEquals(FailureKind.UNAVAILABLE, (result as Result.Failed).failure.kind)
                    assertTrue(
                        Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(2)
                    )
                }
        } finally {
            server.stop(0)
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
