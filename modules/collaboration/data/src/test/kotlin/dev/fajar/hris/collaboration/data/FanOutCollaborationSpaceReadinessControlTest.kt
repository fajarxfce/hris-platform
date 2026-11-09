package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class FanOutCollaborationSpaceReadinessControlTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun oneCurrentTargetIsSentOutsideTransactionAndOnlyMatrixCompletes() {
        val f = SpaceReadinessFanoutFixture(postgres)
        val push = f.rows().single { it["channel"] == "PUSH" }
        val lease = f.claim()
        val checkpoint = requireNotNull(f.checkpoint(lease))
        // Passive readiness does not need a fresh short-lived interactive bearer.
        f.e.s.owner.update(
            "update native_sessions set access_expires_at=now()-interval '1 second' where id=?",
            f.e.peerParent,
        )
        f.success(f.command().fanOut(lease))
        assertNull(f.checkpoint(lease))
        assertEquals(1, f.deliveries.size)
        assertEquals(lease.eventId, f.deliveries.single().eventId)
        assertEquals(checkpoint.publicationRevision, f.deliveries.single().publicationRevision)
        assertEquals(
            checkpoint.publicationCutoff.toEpochMilli(),
            f.deliveries.single().publishedAtEpochMillis,
        )
        assertEquals(push, f.rows().single { it["channel"] == "PUSH" })
        assertTrue(f.success(f.command().claim(UUID.randomUUID(), 4)).isEmpty())
        assertInstanceOf(Result.Failed::class.java, f.command().fanOut(lease))
        assertEquals(1, f.deliveries.size)
    }

    @Test
    fun providerFailureReleasesWithBoundedRetryAndNoCursorProgress() {
        val f = SpaceReadinessFanoutFixture(postgres)
        val lease = f.claim()
        f.outcome = {
            Result.Failed(
                Failure(
                    FailureKind.UNAVAILABLE,
                    "fixture_busy",
                    parameters = mapOf("retryAfterSeconds" to "9999"),
                )
            )
        }
        val before = f.now()
        f.success(f.command().fanOut(lease))
        val row = f.rows().single { it["channel"] == "MATRIX" }
        assertNull(row["after_account_id"])
        assertNull(row["lease_token"])
        assertNull(row["completed_at"])
        assertEquals(1, row["attempts"])
        val next = (row["available_at"] as java.sql.Timestamp).toInstant()
        assertTrue(next >= before.plusSeconds(300))
        assertTrue(next <= f.now().plusSeconds(300))
        assertTrue(f.success(f.command().claim(UUID.randomUUID(), 1)).isEmpty())
        assertEquals(1, f.deliveries.size)
    }

    @Test
    fun expiredProviderIsDeferredWithoutIssuanceOrTransport() {
        val f = SpaceReadinessFanoutFixture(postgres)
        f.rotatePeer(expiresAt = f.now().plusSeconds(2))
        val lease = f.claim()
        val clock = SpaceGatewayClock(f.now().plusSeconds(3))
        val providers =
            f.e.s.owner.queryForList(
                "select * from collaboration_space_provider_sessions order by device_id"
            )
        f.success(f.command(clock = clock).fanOut(lease))
        assertTrue(f.deliveries.isEmpty())
        assertEquals(
            providers,
            f.e.s.owner.queryForList(
                "select * from collaboration_space_provider_sessions order by device_id"
            ),
        )
        val row = f.rows().single { it["channel"] == "MATRIX" }
        assertNull(row["after_account_id"])
        assertNull(row["lease_token"])
        assertNull(row["completed_at"])
    }

    @Test
    fun providerGenerationChangedDuringIoDefersWithoutAcknowledgingStaleAdmission() {
        val f = SpaceReadinessFanoutFixture(postgres)
        val lease = f.claim()
        f.outcome = {
            f.rotatePeer(token = "fixture-replaced-token")
            Result.Success(Unit)
        }
        f.success(f.command().fanOut(lease))
        assertEquals(1, f.deliveries.size)
        val row = f.rows().single { it["channel"] == "MATRIX" }
        assertNull(row["after_account_id"])
        assertNull(row["completed_at"])
        assertNull(row["lease_token"])
    }

    @Test
    fun revokedParentAfterEitherProviderOutcomeIsSuppressedUnderCurrentGuards() {
        val f = SpaceReadinessFanoutFixture(postgres)
        val lease = f.claim()
        f.outcome = {
            f.e.s.owner.update(
                "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                f.e.peerParent,
            )
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_lost_response"))
        }
        f.success(f.command().fanOut(lease))
        val row = f.rows().single { it["channel"] == "MATRIX" }
        assertEquals(f.e.peer, row["after_account_id"])
        assertNotNull(row["completed_at"])
        assertEquals(0, row["attempts"])
        assertEquals(1, f.deliveries.size)
    }

    @Test
    fun lateTargetWriteFailureAndCancellationRollBackTheActualCursor() {
        val f = SpaceReadinessFanoutFixture(postgres)
        val lease = f.claim()
        val original = requireNotNull(f.checkpoint(lease))
        for (cancel in listOf(false, true)) {
            var wrote = false
            val queue =
                object : CollaborationSpaceReadinessQueueRepository by f.queue {
                    override fun advance(
                        checkpoint: CollaborationSpaceReadinessCheckpoint,
                        next: CollaborationReadinessPosition,
                    ): Result<Boolean> =
                        f.queue.advance(checkpoint, next).flatMap { value ->
                            assertTrue(value)
                            wrote = true
                            if (cancel)
                                throw CancellationException("fixture cancelled after cursor write")
                            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_after_write"))
                        }
                }
            val command = f.command(queue = queue)
            if (cancel) assertThrows(CancellationException::class.java) { command.fanOut(lease) }
            else assertInstanceOf(Result.Failed::class.java, command.fanOut(lease))
            assertTrue(wrote)
            assertEquals(original, f.checkpoint(lease))
        }
        assertEquals(2, f.deliveries.size)
        assertEquals(
            f.deliveries[0],
            f.deliveries[1],
            "Lost acknowledgement reuses stable provider transaction identity",
        )
    }

    @Test
    fun finalDirectoryRecheckRollsBackAnOtherwiseSuccessfulAcknowledgement() {
        val f = SpaceReadinessFanoutFixture(postgres)
        val lease = f.claim()
        val original = requireNotNull(f.checkpoint(lease))
        var wrote = false
        val queue =
            object : CollaborationSpaceReadinessQueueRepository by f.queue {
                override fun advance(
                    checkpoint: CollaborationSpaceReadinessCheckpoint,
                    next: CollaborationReadinessPosition,
                ): Result<Boolean> =
                    f.queue.advance(checkpoint, next).map { value ->
                        assertTrue(value)
                        wrote = true
                        f.e.revokePeer()
                        value
                    }
            }
        assertInstanceOf(Result.Failed::class.java, f.command(queue = queue).fanOut(lease))
        assertTrue(wrote)
        assertEquals(original, f.checkpoint(lease))
        assertEquals(1, f.deliveries.size)
    }

    @Test
    fun cancellationAndElapsedBudgetDoNotAcknowledgeOrStartAnotherRequest() {
        val f = SpaceReadinessFanoutFixture(postgres)
        val lease = f.claim()
        val original = requireNotNull(f.checkpoint(lease))
        f.outcome = { throw CancellationException("fixture provider cancellation") }
        assertThrows(CancellationException::class.java) { f.command().fanOut(lease) }
        assertEquals(original, f.checkpoint(lease))
        val clock = SpaceGatewayClock(f.now())
        f.outcome = {
            clock.at = clock.at.plusSeconds(21)
            Result.Success(Unit)
        }
        assertInstanceOf(Result.Failed::class.java, f.command(clock = clock).fanOut(lease))
        assertEquals(original, f.checkpoint(lease))
        assertEquals(2, f.deliveries.size)
    }

    @Test
    fun wrongChannelAndMalformedTargetPageNeverReachTheProvider() {
        val f = SpaceReadinessFanoutFixture(postgres)
        val lease = f.claim()
        val original = requireNotNull(f.checkpoint(lease))
        assertInstanceOf(
            Result.Failed::class.java,
            f.command().fanOut(lease.copy(channel = CollaborationSpaceReadinessChannel.PUSH)),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.command().fanOut(lease.copy(token = UUID.randomUUID())),
        )
        val queue =
            object : CollaborationSpaceReadinessQueueRepository by f.queue {
                override fun targets(
                    checkpoint: CollaborationSpaceReadinessCheckpoint,
                    limit: Int,
                ): Result<List<CollaborationReadinessTarget>> =
                    f.queue.targets(checkpoint, limit).map { it + it }
            }
        assertInstanceOf(Result.Failed::class.java, f.command(queue = queue).fanOut(lease))
        assertEquals(original, f.checkpoint(lease))
        assertTrue(f.deliveries.isEmpty())
    }

    @Test
    fun providerWriterCannotPassDeviceGuardUntilTargetAcknowledgementCommits() {
        val f = SpaceReadinessFanoutFixture(postgres)
        val lease = f.claim()
        val executor = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val writerPid = AtomicInteger()
        var writer: Future<*>? = null
        val queue =
            object : CollaborationSpaceReadinessQueueRepository by f.queue {
                override fun advance(
                    checkpoint: CollaborationSpaceReadinessCheckpoint,
                    next: CollaborationReadinessPosition,
                ): Result<Boolean> {
                    writer =
                        executor.submit {
                            f.rotatePeer(
                                token = "fixture-competing-token",
                                entered = {
                                    writerPid.set(
                                        f.e.s.runtime.queryForObject(
                                            "select pg_backend_pid()",
                                            Int::class.java,
                                        )!!
                                    )
                                    entered.countDown()
                                },
                            )
                        }
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    var blocked = false
                    while (!blocked && System.nanoTime() < deadline) {
                        blocked =
                            f.e.s.owner.queryForObject(
                                "select count(*) from pg_stat_activity where pid=? and wait_event_type='Lock'",
                                Int::class.java,
                                writerPid.get(),
                            ) == 1
                        if (!blocked) Thread.sleep(10)
                    }
                    assertTrue(blocked, "Actual provider writer must wait on the admitted device")
                    assertFalse(requireNotNull(writer).isDone)
                    return f.queue.advance(checkpoint, next)
                }
            }
        try {
            f.success(f.command(queue = queue).fanOut(lease))
            requireNotNull(writer).get(5, TimeUnit.SECONDS)
            assertNull(f.checkpoint(lease))
            assertNotNull(f.rows().single { it["channel"] == "MATRIX" }["completed_at"])
            assertEquals(1, f.deliveries.size)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
