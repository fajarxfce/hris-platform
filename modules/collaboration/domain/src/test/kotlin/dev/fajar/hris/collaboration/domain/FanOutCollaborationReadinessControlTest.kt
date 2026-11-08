package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.FanOutCollaborationReadinessControl
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import java.lang.reflect.Proxy
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FanOutCollaborationReadinessControlTest {
    @Test
    fun ownCurrentTargetsSendOutsideTransactionsWithoutPushOrCredentialCreation() {
        val f = Fixture(3)
        success(f.useCase.fanOut(f.lease))
        assertEquals(f.targets.map { it.position.deviceId }, f.sent.map { it.deviceId })
        assertTrue(f.complete)
        assertEquals(3, f.sent.map { it.deviceId }.distinct().size)
        assertEquals(setOf(f.lease.eventId), f.sent.map { it.eventId }.toSet())
        assertEquals(
            listOf("policy", "company", "membership", "account", "parent"),
            f.guards.take(5),
        )
    }

    @Test
    fun lostProviderResponseReleasesOnlyControlLeaseAndRetriesExactOriginal() {
        val f = Fixture(1)
        f.sendResult = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_lost_response"))
        success(f.useCase.fanOut(f.lease))
        assertEquals(1, f.sent.size)
        assertNull(f.checkpoint.after)
        assertFalse(f.complete)
        assertFalse(f.leased)
        assertEquals(f.now.plusSeconds(2), f.availableAt)
        f.sendResult = Result.Success(Unit)
        f.now = f.availableAt
        val lease = success(f.useCase.claim(UUID.randomUUID(), 1)).single()
        success(f.useCase.fanOut(lease))
        assertEquals(listOf(f.sent.first(), f.sent.first()), f.sent)
        assertTrue(f.complete)
    }

    @Test
    fun revocationOrParentReplacementCannotBorrowTheNewOwner() {
        for (change in
            listOf<(Participant) -> Unit>(
                { it.parent = it.parent.copy(revokedAt = it.now) },
                { it.parent = it.parent.copy(id = UUID.randomUUID()) },
                { it.access = it.access.copy(permissions = emptySet()) },
                { it.device = it.device.copy(state = CollaborationDeviceState.REVOKED) },
                { it.gateway = it.gateway.copy(nativeSessionId = UUID.randomUUID()) },
                { it.keys = it.keys.copy(version = 0, publishedAt = null) },
            )) {
            val f = Fixture(1)
            change(f.participants.single())
            success(f.useCase.fanOut(f.lease))
            assertTrue(f.sent.isEmpty())
            assertEquals(0, f.credentialReads)
            assertTrue(f.complete)
        }
    }

    @Test
    fun revocationDuringSendSkipsOldTargetAndCredentialRotationDefersUncertainOutcome() {
        val revoked = Fixture(1)
        revoked.afterSend = {
            revoked.participants.single().parent =
                revoked.participants.single().parent.copy(revokedAt = revoked.now)
        }
        success(revoked.useCase.fanOut(revoked.lease))
        assertTrue(revoked.complete)
        assertEquals(1, revoked.sent.size)
        val rotated = Fixture(1)
        rotated.afterSend = { rotated.participants.single().generation++ }
        success(rotated.useCase.fanOut(rotated.lease))
        assertNull(rotated.checkpoint.after)
        assertFalse(rotated.leased)
        assertEquals(1, rotated.sent.size)
    }

    @Test
    fun unavailableCredentialsDeferWithoutLoggingInAndDeadlineCannotCommitLateAck() {
        val missing = Fixture(1)
        missing.participants.single().credentialAvailable = false
        success(missing.useCase.fanOut(missing.lease))
        assertTrue(missing.sent.isEmpty())
        assertFalse(missing.leased)
        assertNull(missing.checkpoint.after)
        val late = Fixture(1)
        late.afterSend = { late.now = late.now.plusSeconds(21) }
        assertInstanceOf(Result.Failed::class.java, late.useCase.fanOut(late.lease))
        assertEquals(1, late.sent.size)
        assertNull(late.checkpoint.after)
        assertFalse(late.complete)
    }

    @Test
    fun lostCheckpointReplyKeepsCommittedProgressAndCancellationRollsBackUncommittedAdvance() {
        val lost = Fixture(2)
        lost.loseCommitReply = true
        assertThrows(IllegalStateException::class.java) { lost.useCase.fanOut(lost.lease) }
        assertEquals(lost.targets.first().position, lost.checkpoint.after)
        success(lost.useCase.fanOut(lost.lease))
        assertEquals(2, lost.sent.size)
        val cancelled = Fixture(1)
        cancelled.interruptAdvance = true
        assertThrows(InterruptedException::class.java) { cancelled.useCase.fanOut(cancelled.lease) }
        assertNull(cancelled.checkpoint.after)
        assertEquals(1, cancelled.sent.size)
    }

    @Test
    fun finitePagesNeedAnotherLeaseAndStaleLeaseOrMalformedPageCannotSend() {
        val f = Fixture(9)
        success(f.useCase.fanOut(f.lease))
        assertEquals(8, f.sent.size)
        assertFalse(f.complete)
        assertInstanceOf(Result.Failed::class.java, f.useCase.fanOut(f.lease))
        success(f.useCase.fanOut(success(f.useCase.claim(UUID.randomUUID(), 1)).single()))
        assertTrue(f.complete)
        assertEquals(9, f.sent.size)
        for (malformed in listOf("duplicate", "reverse", "overflow")) {
            val bad = Fixture(9)
            bad.malformed = malformed
            assertInstanceOf(Result.Failed::class.java, bad.useCase.fanOut(bad.lease))
            assertTrue(bad.sent.isEmpty())
        }
    }

    @Test
    fun shortProofExpiryAllowsOnlyOpaqueWakeWhileHardExpiryAndClientPolicySuppressIt() {
        val f = Fixture(1)
        val peer = f.participants.single()
        val control = f.control(peer)
        val ready = peer.admitted(control, f.now)!!
        assertTrue(peer.parent.accessExpiresAt < f.now)
        assertTrue(peer.gateway.accessExpiresAt < f.now)
        assertNull(peer.parent.mfaVerifiedAt)
        assertEquals(f.now.plusSeconds(20), ready.expiresAt)
        assertNotNull(
            peer.admitted(control, f.now, EffectiveClientPolicy(null, f.now.plusSeconds(3)))
        )
        assertEquals(
            f.now.plusSeconds(3),
            peer
                .admitted(control, f.now, EffectiveClientPolicy(null, f.now.plusSeconds(3)))!!
                .expiresAt,
        )
        assertNull(peer.admitted(control, f.now, EffectiveClientPolicy(null, f.now)))
        for (policy in
            listOf(
                ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)),
                ClientPolicy(minimumBuilds = MinimumClientBuilds(android = 99)),
            )) {
            val effective =
                EffectiveClientPolicy(
                    CompanyClientPolicyRevision(
                        0,
                        f.now.minusSeconds(60),
                        policy,
                        f.now.minusSeconds(120),
                        peer.account.id,
                        "Fixture",
                    ),
                    null,
                )
            assertNull(peer.admitted(control, f.now, effective))
        }
        peer.parent = peer.parent.copy(expiresAt = f.now)
        assertNull(peer.admitted(control, f.now))
    }

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }

    private class Participant(val now: Instant, val company: UUID, number: Int) {
        val account =
            Account(
                UUID(0, number.toLong()),
                "fixture@example.test",
                "Fixture",
                true,
                true,
                0,
                securityVersion = 2,
            )
        var access = AccountAccess(account, setOf(COLLABORATION_CHAT_PERMISSION), true, true)
        var parent =
            NativeSession(
                UUID.randomUUID(),
                account.id,
                "Fixture",
                now.minusSeconds(3600),
                now.minusSeconds(4000),
                null,
                2,
                now.plusSeconds(7200),
                now.minusSeconds(10),
                now.minusSeconds(1800),
                null,
                3,
                UUID.randomUUID(),
                null,
            )
        var device =
            CollaborationDevice(
                UUID(0, number.toLong()),
                company,
                account.id,
                parent.id,
                CollaborationDeviceState.ACTIVE,
                1,
                now.minusSeconds(3000),
                null,
                CollaborationPlatform.ANDROID,
            )
        var gateway =
            CollaborationGatewaySession(
                UUID.randomUUID(),
                company,
                account.id,
                device.id,
                parent.id,
                CollaborationPlatform.ANDROID,
                10,
                2,
                now.minusSeconds(2900),
                now.minusSeconds(900),
                now.plusSeconds(3600),
                now.minusSeconds(10),
            )
        var keys =
            CollaborationDeviceKeyPublication(
                company,
                account.id,
                device.id,
                "a".repeat(64),
                1,
                now.minusSeconds(100),
                now.minusSeconds(60),
            )
        var generation = 1L
        var credentialAvailable = true

        fun credential() =
            if (!credentialAvailable) null
            else
                CollaborationProviderSession(
                    company,
                    account.id,
                    device.id,
                    "@${collaborationMatrixLocalpart(company, account.id)}:matrix.example.test",
                    collaborationMatrixDeviceId(device.id),
                    generation,
                    "fixture-provider-token",
                    now.minusSeconds(100),
                    now.minusSeconds(60),
                    now.plusSeconds(600),
                )

        fun admitted(
            control: CollaborationReadinessControl,
            at: Instant,
            policy: EffectiveClientPolicy = EffectiveClientPolicy(null, null),
        ) =
            collaborationReadinessAdmission(
                control,
                access,
                parent,
                device,
                gateway,
                keys,
                policy,
                at,
            )
    }

    private class Fixture(size: Int) {
        var now = Instant.parse("2026-11-08T21:00:00Z")
        val lease =
            CollaborationReadinessLease(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                now.plusSeconds(60),
            )
        var checkpoint = CollaborationReadinessCheckpoint(lease, now.minusSeconds(5), null)
        val participants = (1..size).map { Participant(now, lease.companyId, it) }
        val targets =
            participants.map {
                CollaborationReadinessTarget(
                    CollaborationReadinessPosition(it.account.id, it.device.id),
                    it.parent.id,
                )
            }
        var complete = false
        var leased = true
        var availableAt = now
        var actor: Actor? = null
        var credentialReads = 0
        var malformed: String? = null
        var loseCommitReply = false
        var interruptAdvance = false
        val sent = mutableListOf<CollaborationReadinessControl>()
        val guards = mutableListOf<String>()
        var sendResult: Result<Unit> = Result.Success(Unit)
        var afterSend: () -> Unit = {}

        fun control(peer: Participant) =
            CollaborationReadinessControl(
                lease.eventId,
                lease.companyId,
                peer.account.id,
                peer.parent.id,
                peer.device.id,
                checkpoint.publicationCutoff.toEpochMilli(),
            )

        private fun participant() =
            participants.single { it.account.id == checkNotNull(actor).accountId }

        private val clock =
            object : Clock() {
                override fun getZone() = ZoneOffset.UTC

                override fun withZone(zone: ZoneId): Clock = this

                override fun instant() = now
            }
        private val tx =
            object : TransactionRunner {
                override fun <T> run(actor: Actor, operation: () -> Result<T>): Result<T> {
                    check(this@Fixture.actor == null)
                    this@Fixture.actor = actor
                    val before = checkpoint
                    val beforeComplete = complete
                    val beforeLeased = leased
                    val beforeAvailable = availableAt
                    fun rollback() {
                        checkpoint = before
                        complete = beforeComplete
                        leased = beforeLeased
                        availableAt = beforeAvailable
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
                    if (checkpoint.after != before.after && loseCommitReply) {
                        loseCommitReply = false
                        error("fixture_ack_lost")
                    }
                    return result
                }
            }
        private val queue =
            object : CollaborationReadinessControlQueueRepository {
                override fun claim(
                    ownerId: UUID,
                    limit: Int,
                ): Result<List<CollaborationReadinessLease>> {
                    if (complete || leased || now < availableAt) return Result.Success(emptyList())
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
                        when (malformed) {
                            "duplicate" -> listOf(page.first(), page.first())
                            "reverse" -> page.reversed()
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
                    if (checkpoint != this@Fixture.checkpoint) return Result.Success(false)
                    this@Fixture.checkpoint = checkpoint.copy(after = next)
                    if (interruptAdvance) throw InterruptedException()
                    return Result.Success(true)
                }

                override fun release(
                    checkpoint: CollaborationReadinessCheckpoint,
                    complete: Boolean,
                    availableAt: Instant,
                ): Result<Boolean> {
                    if (checkpoint != this@Fixture.checkpoint) return Result.Success(false)
                    this@Fixture.complete = complete
                    this@Fixture.availableAt = availableAt
                    leased = false
                    return Result.Success(true)
                }
            }
        val useCase =
            FanOutCollaborationReadinessControl(
                queue,
                port { name ->
                    check(name == "find")
                    credentialReads++
                    Result.Success(participant().credential())
                },
                object : CollaborationMatrixReadinessRepository {
                    override fun send(
                        control: CollaborationReadinessControl,
                        serverName: String,
                        token: String,
                        deadline: Instant,
                    ): Result<Unit> {
                        check(actor == null)
                        check(deadline > now && deadline <= now.plusSeconds(20))
                        sent.add(control)
                        afterSend()
                        return sendResult
                    }
                },
                port { name ->
                    check(name == "findBound")
                    Result.Success(participant().device)
                },
                port { name ->
                    check(name == "find")
                    Result.Success(participant().gateway)
                },
                port { name ->
                    check(name == "find")
                    Result.Success(participant().keys)
                },
                port { name ->
                    check(name == "lock")
                    guards.add("parent")
                    Result.Success(participant().parent)
                },
                port { name ->
                    when (name) {
                        "lock" -> {
                            guards.add("policy")
                            Result.Success(Unit)
                        }
                        "effective" -> Result.Success(EffectiveClientPolicy(null, null))
                        else -> error(name)
                    }
                },
                port { name ->
                    check(name == "lock")
                    guards.add("company")
                    Result.Success(Unit)
                },
                port { name ->
                    check(name == "lock")
                    guards.add("membership")
                    Result.Success(Unit)
                },
                port { name ->
                    when (name) {
                        "lockAccount" -> {
                            guards.add("account")
                            Result.Success(Unit)
                        }
                        "access" -> Result.Success(participant().access)
                        else -> error(name)
                    }
                },
                tx,
                CollaborationServicePolicy(true, false),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                clock,
            )
    }

    companion object {
        /**
         * Strict test ports fail on any unexpected mutation, login, refresh or nested
         * orchestration.
         */
        private inline fun <reified T> port(crossinline operation: (String) -> Any): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _
                ->
                operation(method.name)
            } as T
    }
}
