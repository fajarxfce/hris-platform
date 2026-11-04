package dev.fajar.hris.collaboration.delivery

import dev.fajar.hris.collaboration.delivery.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixPushCallbackExecutorTest {
    private val hint =
        MatrixPushBatch(
            null,
            listOf(MatrixPushDeviceKey.Company(CollaborationPushKey("hriscp1_" + "a".repeat(64)))),
        )

    private fun success(): Result<MatrixPushBatchReceipt> =
        Result.Success(MatrixPushBatchReceipt(emptyList()))

    @Test
    fun fourLiveOperationsRejectExtraWorkWithoutAWaitingQueue() {
        val entered = CountDownLatch(4)
        val release = CountDownLatch(1)
        val clients = Executors.newFixedThreadPool(4)
        val calls = AtomicInteger()
        val executor =
            MatrixPushCallbackExecutor(
                { _, _, deadline ->
                    assertTrue(deadline > java.time.Instant.now())
                    calls.incrementAndGet()
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    success()
                },
                Clock.systemUTC(),
            )
        try {
            val pending =
                List(4) {
                    clients.submit<Result<MatrixPushBatchReceipt>> {
                        executor.accept(hint, UUID.randomUUID())
                    }
                }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            assertInstanceOf(Result.Failed::class.java, executor.accept(hint, UUID.randomUUID()))
            assertEquals(4, calls.get())
            release.countDown()
            pending.forEach {
                assertInstanceOf(Result.Success::class.java, it.get(3, TimeUnit.SECONDS))
            }
        } finally {
            release.countDown()
            executor.close()
            clients.shutdownNow()
            assertTrue(clients.awaitTermination(3, TimeUnit.SECONDS))
        }
    }

    @Test
    fun timeoutCancelsItsOwnTaskAndCannotInterruptTheNextReusedThread() {
        val interrupted = CountDownLatch(1)
        val calls = AtomicInteger()
        MatrixPushCallbackExecutor(
                { _, _, _ ->
                    if (calls.incrementAndGet() == 1) {
                        try {
                            CountDownLatch(1).await(5, TimeUnit.SECONDS)
                        } catch (error: InterruptedException) {
                            interrupted.countDown()
                            throw error
                        }
                    }
                    assertFalse(Thread.currentThread().isInterrupted)
                    success()
                },
                Clock.systemUTC(),
                Duration.ofMillis(150),
            )
            .use { executor ->
                assertInstanceOf(
                    Result.Failed::class.java,
                    executor.accept(hint, UUID.randomUUID()),
                )
                assertTrue(interrupted.await(2, TimeUnit.SECONDS))
                repeat(12) {
                    assertInstanceOf(
                        Result.Success::class.java,
                        executor.accept(hint, UUID.randomUUID()),
                    )
                }
            }
    }

    @Test
    fun timedOutButStillRunningOperationsContinueToConsumeCapacity() {
        val entered = CountDownLatch(4)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(4)
        val clients = Executors.newFixedThreadPool(4)
        val executor =
            MatrixPushCallbackExecutor(
                { _, _, _ ->
                    entered.countDown()
                    try {
                        // Model a bounded JDBC operation that cannot react immediately to
                        // interruption.
                        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                        while (release.count > 0 && System.nanoTime() < until) {
                            try {
                                release.await(50, TimeUnit.MILLISECONDS)
                            } catch (_: InterruptedException) {}
                        }
                        success()
                    } finally {
                        exited.countDown()
                    }
                },
                Clock.systemUTC(),
                Duration.ofMillis(300),
            )
        try {
            val pending =
                List(4) {
                    clients.submit<Result<MatrixPushBatchReceipt>> {
                        executor.accept(hint, UUID.randomUUID())
                    }
                }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            pending.forEach {
                assertInstanceOf(Result.Failed::class.java, it.get(2, TimeUnit.SECONDS))
            }
            assertInstanceOf(Result.Failed::class.java, executor.accept(hint, UUID.randomUUID()))
            assertEquals(4, exited.count)
            release.countDown()
            assertTrue(exited.await(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.close()
            clients.shutdownNow()
            assertTrue(clients.awaitTermination(3, TimeUnit.SECONDS))
        }
    }

    @Test
    fun shutdownCancelsTheOwnedOperationAndReleasesItsWaitingCaller() {
        val entered = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        val executor =
            MatrixPushCallbackExecutor(
                { _, _, _ ->
                    entered.countDown()
                    try {
                        CountDownLatch(1).await(5, TimeUnit.SECONDS)
                    } catch (error: InterruptedException) {
                        interrupted.countDown()
                        throw error
                    }
                    success()
                },
                Clock.systemUTC(),
            )
        try {
            val pending =
                caller.submit<Result<MatrixPushBatchReceipt>> {
                    executor.accept(hint, UUID.randomUUID())
                }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            executor.close()
            assertTrue(interrupted.await(2, TimeUnit.SECONDS))
            assertInstanceOf(Result.Failed::class.java, pending.get(2, TimeUnit.SECONDS))
            assertInstanceOf(Result.Failed::class.java, executor.accept(hint, UUID.randomUUID()))
        } finally {
            executor.close()
            caller.shutdownNow()
            assertTrue(caller.awaitTermination(3, TimeUnit.SECONDS))
        }
    }

    @Test
    fun callerInterruptionCancelsTheWorkerAndDomainCancellationStillPropagates() {
        val entered = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val callerDone = CountDownLatch(1)
        val callerError = AtomicReference<Throwable>()
        val executor =
            MatrixPushCallbackExecutor(
                { _, _, _ ->
                    entered.countDown()
                    try {
                        CountDownLatch(1).await(5, TimeUnit.SECONDS)
                    } catch (error: InterruptedException) {
                        interrupted.countDown()
                        throw error
                    }
                    success()
                },
                Clock.systemUTC(),
            )
        val caller =
            Thread.ofPlatform().unstarted {
                try {
                    executor.accept(hint, UUID.randomUUID())
                } catch (error: Throwable) {
                    callerError.set(error)
                } finally {
                    callerDone.countDown()
                }
            }
        try {
            caller.start()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            caller.interrupt()
            assertTrue(callerDone.await(2, TimeUnit.SECONDS))
            assertInstanceOf(InterruptedException::class.java, callerError.get())
            assertTrue(interrupted.await(2, TimeUnit.SECONDS))
        } finally {
            caller.interrupt()
            caller.join(3000)
            assertFalse(caller.isAlive)
            executor.close()
        }
        for (error in listOf(CancellationException("fixture"), InterruptedException("fixture"))) {
            MatrixPushCallbackExecutor({ _, _, _ -> throw error }, Clock.systemUTC()).use { owner ->
                assertSame(
                    error,
                    assertThrows(error.javaClass) { owner.accept(hint, UUID.randomUUID()) },
                )
            }
        }
    }
}
