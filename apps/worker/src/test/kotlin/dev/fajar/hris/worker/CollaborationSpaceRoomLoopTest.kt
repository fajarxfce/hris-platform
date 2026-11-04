package dev.fajar.hris.worker

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.domain.map
import dev.fajar.hris.worker.collaboration.CollaborationSpaceRoomWorker
import dev.fajar.hris.worker.collaboration.CollaborationSpaceRoomWorkerSettings
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

class CollaborationSpaceRoomLoopTest {
    @Test
    fun localParallelismAndDeadlinesBoundDeliveryWithoutAWaitingTaskQueue() {
        val first = lease()
        val second =
            lease().let {
                it.copy(
                    request =
                        it.request.copy(
                            spaceId = first.request.spaceId,
                            accountId = first.request.accountId,
                            deviceId = first.request.deviceId,
                        )
                )
            }
        val pending = ConcurrentLinkedQueue(listOf(first, second))
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
                advance = {
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
                advance = {
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
                advance = { error("Shutdown cannot schedule delivery") },
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
            CollaborationSpaceRoomWorkerSettings(parallelism = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollaborationSpaceRoomWorkerSettings(parallelism = 9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollaborationSpaceRoomWorkerSettings(pollInterval = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollaborationSpaceRoomWorkerSettings(processingTimeout = Duration.ofSeconds(56))
        }
        assertEquals(
            Duration.ofSeconds(45),
            CollaborationSpaceRoomWorkerSettings().processingTimeout,
        )
    }

    @Test
    fun failedAdvanceSettlesOnceWhileSuccessDoesNotCreateAnotherOutcome() {
        val failed = lease()
        val successful = lease()
        val pending = ConcurrentLinkedQueue(listOf(failed, successful))
        val advanced = CountDownLatch(2)
        val settled = CountDownLatch(1)
        val outcomes = AtomicInteger()
        val cause = Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable")
        Fixture(
                2,
                Duration.ofSeconds(2),
                claim = { _, limit ->
                    Result.Success(buildList { repeat(limit) { pending.poll()?.let { add(it) } } })
                },
                advance = {
                    advanced.countDown()
                    if (it.token == failed.token) Result.Failed(cause) else Result.Success(Unit)
                },
                settle = { item, failure ->
                    assertEquals(failed.token, item.token)
                    assertEquals(cause, failure)
                    outcomes.incrementAndGet()
                    settled.countDown()
                    Result.Success(true)
                },
            )
            .use { f ->
                f.worker.start()
                assertTrue(advanced.await(3, TimeUnit.SECONDS))
                assertTrue(settled.await(3, TimeUnit.SECONDS))
                await { f.tasks.activeCount == 0 }
                assertEquals(1, outcomes.get())
                assertTrue(f.tasks.threadPoolExecutor.queue.isEmpty())
                assertTrue(f.timers.scheduledThreadPoolExecutor.queue.size <= 1)
            }
    }

    @Test
    fun invalidClaimsAndRejectedSubmissionsReleaseReservedCapacity() {
        for (mode in listOf("owner", "oversized", "duplicate", "peer", "zero", "submission")) {
            val calls = AtomicInteger()
            val advances = AtomicInteger()
            lateinit var fixture: Fixture
            fixture =
                Fixture(
                    2,
                    Duration.ofSeconds(1),
                    claim = { owner, _ ->
                        if (calls.incrementAndGet() > 1) Result.Success(emptyList())
                        else {
                            val item = lease().copy(ownerId = owner)
                            Result.Success(
                                when (mode) {
                                    "owner" -> listOf(item.copy(ownerId = UUID.randomUUID()))
                                    "oversized" -> List(3) { lease().copy(ownerId = owner) }
                                    "duplicate" -> listOf(item, item)
                                    "zero" -> listOf(item.copy(token = UUID(0, 0)))
                                    "peer" ->
                                        listOf(
                                            item,
                                            item.copy(
                                                token = UUID.randomUUID(),
                                                request =
                                                    item.request.copy(
                                                        accountId = UUID.randomUUID(),
                                                        deviceId = UUID.randomUUID(),
                                                    ),
                                            ),
                                        )
                                    else -> {
                                        fixture.tasks.shutdown()
                                        listOf(item)
                                    }
                                }
                            )
                        }
                    },
                    advance = {
                        advances.incrementAndGet()
                        Result.Success(Unit)
                    },
                    preserveClaimOwner = true,
                )
            fixture.use { f ->
                f.worker.start()
                await { calls.get() >= 2 }
                assertEquals(0, advances.get())
                assertTrue(f.tasks.threadPoolExecutor.queue.isEmpty())
                assertTrue(f.timers.scheduledThreadPoolExecutor.queue.size <= 1)
            }
        }
    }

    @Test
    fun absoluteLeaseDeadlineWinsAndExpiredClaimsNeverStart() {
        val entered = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val calls = AtomicInteger()
        val id = AtomicReference<UUID>()
        Fixture(
                2,
                Duration.ofSeconds(5),
                claim = { owner, _ ->
                    if (calls.incrementAndGet() > 1) Result.Success(emptyList())
                    else {
                        val live =
                            lease().copy(ownerId = owner, until = Instant.now().plusSeconds(1))
                        id.set(live.token)
                        Result.Success(
                            listOf(
                                lease()
                                    .copy(ownerId = owner, until = Instant.now().minusSeconds(1)),
                                live,
                            )
                        )
                    }
                },
                advance = {
                    assertEquals(id.get(), it.token)
                    entered.countDown()
                    try {
                        CountDownLatch(1).await(5, TimeUnit.SECONDS)
                        error("Absolute lease deadline did not cancel the task")
                    } catch (error: InterruptedException) {
                        interrupted.countDown()
                        throw error
                    }
                },
                preserveClaimOwner = true,
            )
            .use { f ->
                f.worker.start()
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                assertTrue(interrupted.await(3, TimeUnit.SECONDS))
                await { f.tasks.activeCount == 0 && calls.get() > 1 }
                assertTrue(f.timers.scheduledThreadPoolExecutor.queue.size <= 1)
            }
    }

    @Test
    fun stoppingBeforeStartAndFailedStartClosePoolsAndForbidRestart() {
        Fixture(
                1,
                Duration.ofSeconds(1),
                claim = { _, _ -> error("Stopped worker must not claim") },
                advance = { error("Stopped worker must not advance") },
            )
            .use { f ->
                f.worker.stop()
                assertThrows(IllegalStateException::class.java) { f.worker.start() }
            }
        Fixture(
                1,
                Duration.ofSeconds(1),
                claim = { _, _ -> error("Failed worker must not claim") },
                advance = { error("Failed worker must not advance") },
            )
            .use { f ->
                f.timers.shutdown()
                assertThrows(RuntimeException::class.java) { f.worker.start() }
                assertFalse(f.worker.isRunning)
                assertTrue(f.tasks.threadPoolExecutor.isTerminated)
                assertThrows(IllegalStateException::class.java) { f.worker.start() }
            }
    }

    private class Fixture(
        parallelism: Int,
        deadline: Duration,
        claim: (UUID, Int) -> Result<List<CollaborationSpaceRoomLease>>,
        advance: (CollaborationSpaceRoomLease) -> Result<Unit>,
        settle: (CollaborationSpaceRoomLease, Failure) -> Result<Boolean> = { _, _ ->
            error("No failure expected")
        },
        preserveClaimOwner: Boolean = false,
    ) : AutoCloseable {
        val tasks =
            ThreadPoolTaskExecutor().apply {
                corePoolSize = parallelism
                maxPoolSize = parallelism
                setQueueCapacity(0)
                setThreadNamePrefix("space-room-fixture-")
                setWaitForTasksToCompleteOnShutdown(false)
                setAwaitTerminationSeconds(5)
                initialize()
            }
        val timers =
            ThreadPoolTaskScheduler().apply {
                poolSize = 2
                setThreadNamePrefix("space-room-control-fixture-")
                setRemoveOnCancelPolicy(true)
                setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
                setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
                setWaitForTasksToCompleteOnShutdown(false)
                setAwaitTerminationSeconds(5)
                initialize()
            }
        val worker =
            CollaborationSpaceRoomWorker(
                { owner, limit ->
                    claim(owner, limit).map { leases ->
                        if (preserveClaimOwner) leases else leases.map { it.copy(ownerId = owner) }
                    }
                },
                advance,
                settle,
                tasks,
                timers,
                CollaborationSpaceRoomWorkerSettings(parallelism, Duration.ofMillis(25), deadline),
            )

        override fun close() {
            worker.stop()
            assertTrue(tasks.threadPoolExecutor.isTerminated)
            assertTrue(timers.scheduledThreadPoolExecutor.isTerminated)
        }
    }

    private fun lease(): CollaborationSpaceRoomLease {
        val at = Instant.now()
        return CollaborationSpaceRoomLease(
            CollaborationSpaceRoomRequest(
                spaceId = UUID.randomUUID(),
                conversationId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                deviceId = UUID.randomUUID(),
                deviceVersion = 1,
                providerGeneration = 0,
                nativeSessionId = UUID.randomUUID(),
                revision = 0,
                nativeSessionVersion = 0,
                credentialVersion = 0,
                authenticatedAt = at.minusSeconds(60),
                mfaVerifiedAt = null,
                correlationId = UUID.randomUUID(),
                client = ClientVersion(ClientPlatform.ANDROID, 28),
                requestedAt = at,
                expiresAt = at.plusSeconds(60),
            ),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            at.plusSeconds(60),
        )
    }

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
