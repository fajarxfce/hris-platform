package dev.fajar.hris.worker

import dev.fajar.hris.collaboration.domain.entities.CollaborationPushLease
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.worker.push.CollaborationPushWorker
import dev.fajar.hris.worker.push.CollaborationPushWorkerSettings
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

class CollaborationPushLoopTest {
    @Test
    fun localParallelismAndDeadlinesBoundDeliveryWithoutAWaitingTaskQueue() {
        val pending = ConcurrentLinkedQueue(List(2) { lease() })
        val entered = CountDownLatch(2)
        val interrupted = CountDownLatch(2)
        val calls = AtomicInteger()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        Fixture(
                2,
                Duration.ofMillis(500),
                claim = { _, limit ->
                    assertTrue(limit in 1..2)
                    Result.Success(buildList { repeat(limit) { pending.poll()?.let { add(it) } } })
                },
                deliver = {
                    calls.incrementAndGet()
                    val count = active.incrementAndGet()
                    maximum.updateAndGet { previous -> maxOf(previous, count) }
                    entered.countDown()
                    try {
                        CountDownLatch(1).await(5, TimeUnit.SECONDS)
                        fail<Result<Unit>>("Delivery deadline did not interrupt its task")
                    } catch (error: InterruptedException) {
                        interrupted.countDown()
                        throw error
                    } finally {
                        active.decrementAndGet()
                    }
                },
            )
            .use { fixture ->
                fixture.worker.start()
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(interrupted.await(3, TimeUnit.SECONDS))
                await { fixture.tasks.activeCount == 0 }
                assertEquals(2, calls.get())
                assertEquals(2, maximum.get())
                assertEquals(0, fixture.tasks.threadPoolExecutor.queue.size)
                assertTrue(fixture.timers.scheduledThreadPoolExecutor.queue.size <= 1)
            }
    }

    @Test
    fun aTimedOutTaskThatHasNotExitedStillOccupiesItsDeliverySlot() {
        val claims = AtomicInteger()
        val first = lease()
        val interrupted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val fixture =
            Fixture(
                1,
                Duration.ofMillis(150),
                claim = { _, _ ->
                    Result.Success(
                        if (claims.incrementAndGet() == 1) listOf(first) else emptyList()
                    )
                },
                deliver = {
                    try {
                        check(release.await(3, TimeUnit.SECONDS))
                    } catch (_: InterruptedException) {
                        interrupted.countDown()
                        // Simulate a bounded SDK cleanup that is still using the original thread.
                        check(release.await(3, TimeUnit.SECONDS))
                    } finally {
                        exited.countDown()
                    }
                    Result.Success(Unit)
                },
            )
        try {
            fixture.worker.start()
            assertTrue(interrupted.await(3, TimeUnit.SECONDS))
            TimeUnit.MILLISECONDS.sleep(125)
            assertEquals(1, fixture.tasks.activeCount)
            assertEquals(1, claims.get())
            assertTrue(fixture.tasks.threadPoolExecutor.queue.isEmpty())
            release.countDown()
            assertTrue(exited.await(3, TimeUnit.SECONDS))
            await { claims.get() > 1 }
        } finally {
            release.countDown()
            fixture.close()
        }
    }

    @Test
    fun shutdownInterruptsAClaimAndClosesOwnedExecutorsWithoutAllowingRestart() {
        val entered = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val callbacks = AtomicInteger()
        Fixture(
                1,
                Duration.ofSeconds(1),
                claim = { _, _ ->
                    entered.countDown()
                    try {
                        CountDownLatch(1).await(5, TimeUnit.SECONDS)
                        error("Claim was not cancelled")
                    } catch (error: InterruptedException) {
                        interrupted.countDown()
                        throw error
                    }
                },
                deliver = { error("Shutdown cannot schedule delivery") },
            )
            .use { fixture ->
                fixture.worker.start()
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                fixture.worker.stop { callbacks.incrementAndGet() }
                assertTrue(interrupted.await(2, TimeUnit.SECONDS))
                assertEquals(1, callbacks.get())
                assertFalse(fixture.worker.isRunning)
                assertTrue(fixture.tasks.threadPoolExecutor.isTerminated)
                assertTrue(fixture.timers.scheduledThreadPoolExecutor.isTerminated)
                assertThrows(IllegalStateException::class.java) { fixture.worker.start() }
            }
    }

    @Test
    fun settingsRejectUnboundedCapacityPollingAndOperationBudgets() {
        assertThrows(IllegalArgumentException::class.java) {
            CollaborationPushWorkerSettings(parallelism = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollaborationPushWorkerSettings(parallelism = 9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollaborationPushWorkerSettings(pollInterval = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollaborationPushWorkerSettings(deliveryTimeout = Duration.ofSeconds(21))
        }
        assertEquals(Duration.ofSeconds(20), CollaborationPushWorkerSettings().deliveryTimeout)
    }

    private class Fixture(
        parallelism: Int,
        deadline: Duration,
        claim: (UUID, Int) -> Result<List<CollaborationPushLease>>,
        deliver: (CollaborationPushLease) -> Result<Unit>,
    ) : AutoCloseable {
        val tasks =
            ThreadPoolTaskExecutor().apply {
                corePoolSize = parallelism
                maxPoolSize = parallelism
                setQueueCapacity(0)
                setThreadNamePrefix("chat-push-fixture-")
                setWaitForTasksToCompleteOnShutdown(false)
                setAwaitTerminationSeconds(5)
                initialize()
            }
        val timers =
            ThreadPoolTaskScheduler().apply {
                poolSize = 2
                setThreadNamePrefix("chat-push-control-fixture-")
                setRemoveOnCancelPolicy(true)
                setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
                setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
                setWaitForTasksToCompleteOnShutdown(false)
                setAwaitTerminationSeconds(5)
                initialize()
            }
        val worker =
            CollaborationPushWorker(
                claim,
                deliver,
                tasks,
                timers,
                CollaborationPushWorkerSettings(parallelism, Duration.ofMillis(25), deadline),
            )

        override fun close() {
            worker.stop()
            assertTrue(tasks.threadPoolExecutor.isTerminated)
            assertTrue(timers.scheduledThreadPoolExecutor.isTerminated)
        }
    }

    private fun lease() =
        CollaborationPushLease(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            Instant.now().plusSeconds(60),
        )

    private fun await(ready: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!ready()) {
            check(System.nanoTime() < until) {
                "Owned worker did not make progress within its budget"
            }
            TimeUnit.MILLISECONDS.sleep(10)
        }
    }
}
