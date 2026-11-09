package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.FanOutCollaborationReadiness
import dev.fajar.hris.core.domain.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FanOutCollaborationReadinessTest {
    @Test
    fun firstPublicationQueuesOnlyOpaqueHintsInEachTargetsOwnTransaction() {
        val f = Fixture(3)
        assertSuccess(f.useCase.fanOut(f.lease))
        assertTrue(f.complete)
        assertEquals(f.targets.map { it.position }, f.queued)
        assertEquals(f.targets.last().position, f.checkpoint.after)
        assertEquals(3, f.committedTargetTransactions)
    }

    @Test
    fun lostResponseAfterCommitResumesAfterOriginalCheckpointWithoutAnotherHint() {
        val f = Fixture(3)
        f.loseFirstCommitResponse = true
        assertThrows(IllegalStateException::class.java) { f.useCase.fanOut(f.lease) }
        assertEquals(listOf(f.targets.first().position), f.queued)
        assertEquals(f.targets.first().position, f.checkpoint.after)
        assertSuccess(f.useCase.fanOut(f.lease))
        assertEquals(f.targets.map { it.position }, f.queued)
        assertTrue(f.complete)
    }

    @Test
    fun checkpointFailureRollsBackAlreadyAppendedHintThenOriginalLeaseCanResume() {
        val f = Fixture(2)
        f.rejectAdvance = true
        assertFailed(f.useCase.fanOut(f.lease))
        assertTrue(f.queued.isEmpty())
        assertNull(f.checkpoint.after)
        f.rejectAdvance = false
        assertSuccess(f.useCase.fanOut(f.lease))
        assertEquals(2, f.queued.size)
    }

    @Test
    fun appendFailureDoesNotAdvanceOrCompletePublication() {
        val f = Fixture(2)
        f.rejectAppend = true
        assertFailed(f.useCase.fanOut(f.lease))
        assertNull(f.checkpoint.after)
        assertFalse(f.complete)
        assertTrue(f.queued.isEmpty())
    }

    @Test
    fun expiredDeadlineAfterAppendRollsBackTheWholeTargetTransaction() {
        val f = Fixture(1)
        f.afterAppend = { f.now = f.now.plusSeconds(21) }
        assertFailed(f.useCase.fanOut(f.lease))
        assertNull(f.checkpoint.after)
        assertTrue(f.queued.isEmpty())
        assertFalse(f.complete)
    }

    @Test
    fun interruptionAfterAppendRollsBackAndPropagatesCancellation() {
        val f = Fixture(1)
        f.afterAppend = { throw InterruptedException() }
        assertThrows(InterruptedException::class.java) { f.useCase.fanOut(f.lease) }
        assertNull(f.checkpoint.after)
        assertTrue(f.queued.isEmpty())
    }

    @Test
    fun staleLeaseCannotAppendOrMoveAnotherOwnersCheckpoint() {
        val f = Fixture(1)
        assertFailed(f.useCase.fanOut(f.lease.copy(token = UUID.randomUUID())))
        assertTrue(f.queued.isEmpty())
        assertNull(f.checkpoint.after)
    }

    @Test
    fun bindingsChangedAfterProjectionAreSkippedWithoutBorrowingNewParent() {
        val f = Fixture(4)
        val positions = f.targets.map { it.position }
        f.currentBindings.remove(positions[0])
        f.currentBindings[positions[1]] =
            f.currentBindings.getValue(positions[1]).copy(nativeSessionId = UUID.randomUUID())
        f.currentBindings[positions[2]] =
            f.currentBindings.getValue(positions[2]).copy(publishedAt = f.now.plusSeconds(1))
        assertSuccess(f.useCase.fanOut(f.lease))
        assertEquals(listOf(positions[3]), f.queued)
        assertEquals(positions[3], f.checkpoint.after)
        assertTrue(f.complete)
    }

    @Test
    fun pagesAreFiniteAndRequireANewLeaseAfterRelease() {
        val f = Fixture(9)
        assertSuccess(f.useCase.fanOut(f.lease))
        assertEquals(8, f.queued.size)
        assertFalse(f.complete)
        assertFailed(f.useCase.fanOut(f.lease))
        val claimed = f.useCase.claim(UUID.randomUUID(), 1) as Result.Success
        assertSuccess(f.useCase.fanOut(claimed.value.single()))
        assertEquals(9, f.queued.distinct().size)
        assertTrue(f.complete)
    }

    @Test
    fun malformedOrRepeatedProjectionStopsBeforeAnyTargetWrite() {
        for (projection in listOf("duplicate", "reverse", "publisher", "overflow")) {
            val f = Fixture(9)
            f.projection = projection
            assertFailed(f.useCase.fanOut(f.lease), projection)
            assertTrue(f.queued.isEmpty())
            assertNull(f.checkpoint.after)
        }
    }

    @Test
    fun disabledServiceAndInvalidBatchDoNotAcquireQueue() {
        val f = Fixture(1)
        assertEquals(
            Result.Success(emptyList<CollaborationReadinessLease>()),
            f.disabled.claim(UUID.randomUUID(), 1),
        )
        assertFailed(f.disabled.fanOut(f.lease))
        assertFailed(f.useCase.claim(UUID.randomUUID(), 5))
        assertFailed(f.useCase.claim(UUID.randomUUID(), 0))
        assertEquals(0, f.claims)
    }

    @Test
    fun checkpointOrderMatchesUnsignedPostgresUuidOrder() {
        val first =
            CollaborationReadinessPosition(
                UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"),
                UUID(0, 1),
            )
        val second =
            CollaborationReadinessPosition(
                UUID.fromString("80000000-0000-0000-0000-000000000000"),
                UUID(0, 1),
            )
        assertTrue(first < second)
        assertTrue(first.accountId > second.accountId)
    }

    private fun assertSuccess(result: Result<*>) =
        assertInstanceOf(Result.Success::class.java, result)

    private fun assertFailed(result: Result<*>, reason: String = "") =
        assertInstanceOf(Result.Failed::class.java, result, reason)

    private class Fixture(size: Int) {
        var now = Instant.parse("2026-11-08T10:00:00Z")
        val lease =
            CollaborationReadinessLease(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID(0, 99),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                now.plusSeconds(60),
            )
        var checkpoint = CollaborationReadinessCheckpoint(lease, now, null)
        var leased = true
        var complete = false
        var claims = 0
        var rejectAppend = false
        var rejectAdvance = false
        var loseFirstCommitResponse = false
        var committedTargetTransactions = 0
        var afterAppend: () -> Unit = {}
        var projection: String? = null
        var actor: Actor? = null
        val queued = mutableListOf<CollaborationReadinessPosition>()
        val targets =
            (1..size).map {
                CollaborationReadinessTarget(
                    CollaborationReadinessPosition(UUID(0, it.toLong()), UUID(0, it.toLong())),
                    UUID.randomUUID(),
                )
            }
        val currentBindings =
            targets
                .associate {
                    it.position to
                        CollaborationPushBinding(
                            lease.companyId,
                            it.position.accountId,
                            it.position.deviceId,
                            it.nativeSessionId,
                            1,
                            now.minusSeconds(2),
                            now.minusSeconds(1),
                        )
                }
                .toMutableMap()
        private val failure = Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rejected"))
        private val clock =
            object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC

                override fun withZone(zone: ZoneId): Clock = this

                override fun instant(): Instant = now
            }
        private val transactions =
            object : TransactionRunner {
                override fun <T> run(actor: Actor, operation: () -> Result<T>): Result<T> {
                    check(this@Fixture.actor == null)
                    this@Fixture.actor = actor
                    val before = checkpoint
                    val beforeQueue = queued.toList()
                    val beforeComplete = complete
                    val beforeLeased = leased
                    fun rollback() {
                        checkpoint = before
                        queued.clear()
                        queued.addAll(beforeQueue)
                        complete = beforeComplete
                        leased = beforeLeased
                    }
                    val result =
                        try {
                            operation().also { if (it is Result.Failed) rollback() }
                        } catch (error: Throwable) {
                            rollback()
                            throw error
                        } finally {
                            this@Fixture.actor = null
                        }
                    if (result is Result.Success && checkpoint.after != before.after) {
                        committedTargetTransactions++
                        if (loseFirstCommitResponse) {
                            loseFirstCommitResponse = false
                            throw IllegalStateException("fixture_commit_response_lost")
                        }
                    }
                    return result
                }
            }
        private val wakes =
            object : CollaborationReadinessWakeRepository {
                override fun claim(
                    ownerId: UUID,
                    limit: Int,
                ): Result<List<CollaborationReadinessLease>> {
                    claims++
                    if (complete || leased) return Result.Success(emptyList())
                    checkpoint =
                        checkpoint.copy(
                            lease =
                                checkpoint.lease.copy(
                                    ownerId = ownerId,
                                    token = UUID.randomUUID(),
                                    expiresAt = now.plusSeconds(60),
                                )
                        )
                    leased = true
                    return Result.Success(listOf(checkpoint.lease))
                }

                override fun find(
                    lease: CollaborationReadinessLease,
                    lock: Boolean,
                ): Result<CollaborationReadinessCheckpoint?> {
                    check(actor?.companyId == lease.companyId)
                    return Result.Success(
                        checkpoint.takeIf {
                            leased && !complete && it.lease == lease && lease.expiresAt > now
                        }
                    )
                }

                override fun targets(
                    checkpoint: CollaborationReadinessCheckpoint,
                    limit: Int,
                ): Result<List<CollaborationReadinessTarget>> {
                    check(actor?.accountId == lease.publisherAccountId)
                    val page =
                        targets
                            .filter { checkpoint.after == null || it.position > checkpoint.after }
                            .take(limit)
                    return Result.Success(
                        when (projection) {
                            "duplicate" -> listOf(page.first(), page.first())
                            "reverse" -> page.reversed()
                            "publisher" ->
                                listOf(
                                    page
                                        .first()
                                        .copy(
                                            position =
                                                CollaborationReadinessPosition(
                                                    lease.publisherAccountId,
                                                    UUID.randomUUID(),
                                                )
                                        )
                                )
                            "overflow" -> targets
                            else -> page
                        }
                    )
                }

                override fun advance(
                    checkpoint: CollaborationReadinessCheckpoint,
                    next: CollaborationReadinessPosition,
                ): Result<Boolean> {
                    check(actor?.accountId == next.accountId)
                    if (rejectAdvance || checkpoint != this@Fixture.checkpoint)
                        return Result.Success(false)
                    this@Fixture.checkpoint = checkpoint.copy(after = next)
                    return Result.Success(true)
                }

                override fun release(
                    checkpoint: CollaborationReadinessCheckpoint,
                    complete: Boolean,
                    availableAt: Instant,
                ): Result<Boolean> {
                    check(actor?.accountId == lease.publisherAccountId)
                    if (checkpoint != this@Fixture.checkpoint) return Result.Success(false)
                    this@Fixture.complete = complete
                    leased = false
                    return Result.Success(true)
                }
            }
        private val bindings =
            object : CollaborationPushBindingRepository {
                override fun find(
                    companyId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                ): Result<CollaborationPushBinding?> {
                    check(actor?.accountId == accountId && actor?.companyId == companyId)
                    return Result.Success(
                        currentBindings[CollaborationReadinessPosition(accountId, deviceId)]
                    )
                }

                override fun lock(companyId: UUID, deviceId: UUID): Result<Unit> =
                    error("unexpected")

                override fun findKey(key: CollaborationPushKey): Result<CollaborationPushBinding?> =
                    error("unexpected")

                override fun reserve(binding: CollaborationPushBinding): Result<Unit> =
                    error("unexpected")

                override fun key(binding: CollaborationPushBinding): Result<CollaborationPushKey?> =
                    error("unexpected")

                override fun publish(
                    binding: CollaborationPushBinding,
                    at: Instant,
                ): Result<Boolean> = error("unexpected")
            }
        private val pushes =
            object : CollaborationPushQueueRepository {
                override fun append(
                    binding: CollaborationPushBinding,
                    event: CollaborationPushEvent?,
                    lifetimeSeconds: Int,
                ): Result<Boolean> {
                    check(
                        actor?.accountId == binding.accountId &&
                            actor?.companyId == binding.companyId
                    )
                    check(event == null && lifetimeSeconds == 300)
                    if (rejectAppend) return failure
                    queued.add(CollaborationReadinessPosition(binding.accountId, binding.deviceId))
                    afterAppend()
                    return Result.Success(true)
                }

                override fun find(
                    companyId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                    lock: Boolean,
                ): Result<CollaborationPushHead?> = error("unexpected")

                override fun claim(
                    ownerId: UUID,
                    limit: Int,
                    leaseSeconds: Int,
                    maximumAttempts: Int,
                ): Result<List<CollaborationPushHead>> = error("unexpected")

                override fun finish(lease: CollaborationPushLease): Result<Boolean> =
                    error("unexpected")

                override fun defer(
                    lease: CollaborationPushLease,
                    availableAt: Instant,
                ): Result<Boolean> = error("unexpected")
            }
        val useCase =
            FanOutCollaborationReadiness(
                wakes,
                bindings,
                pushes,
                transactions,
                CollaborationServicePolicy(true, true),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                clock,
            )
        val disabled =
            FanOutCollaborationReadiness(
                wakes,
                bindings,
                pushes,
                transactions,
                CollaborationServicePolicy(false, true),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                clock,
            )
    }
}
