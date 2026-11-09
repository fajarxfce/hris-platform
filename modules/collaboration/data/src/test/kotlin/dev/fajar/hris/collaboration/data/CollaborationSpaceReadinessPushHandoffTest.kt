package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.push.domain.entities.*
import dev.fajar.hris.push.domain.repositories.PushRepository
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceReadinessPushHandoffTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))
    private val enabled = CollaborationServicePolicy(true, true)

    @Test
    fun readinessHandsOffOneOpaqueWakeAndTheExistingDeliveryOwnerSendsCurrentToken() {
        val f = SpaceReadinessFanoutFixture(postgres, pushPublication = true)
        f.registerPush()
        val matrix = f.rows().single { it["channel"] == "MATRIX" }
        val lease = f.claimPush()
        f.success(f.command(service = enabled).fanOut(lease))
        assertNull(f.checkpoint(lease))
        val head = requireNotNull(f.pushHead())
        assertEquals(1L, head.revision)
        assertEquals(0L, head.deliveredRevision, "Publication handoff is not FCM acknowledgement")
        assertNull(head.event)
        assertTrue(head.expiresAt <= f.now().plusSeconds(300))
        assertEquals(matrix, f.rows().single { it["channel"] == "MATRIX" })
        assertTrue(f.deliveries.isEmpty())
        val calls = mutableListOf<PushMessage>()
        val delivery =
            f.delivery(
                PushRepository { message ->
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
                    calls += message
                    Result.Success(PushOutcome.ACCEPTED)
                }
            )
        val push = f.success(delivery.claim(UUID.randomUUID(), 1)).single()
        f.success(delivery.deliver(push))
        val sent = calls.single()
        assertEquals("fixture-peer-fcm-token", sent.token)
        assertEquals("collaboration.space_chat_available", sent.data["eventCode"])
        assertEquals(f.e.peerParent.toString(), sent.data["nativeSessionId"])
        assertEquals(f.e.s.space.toString(), sent.data["spaceId"])
        assertFalse(
            sent.data.keys.any {
                it in setOf("matrixRoomId", "matrixEventId", "body", "sender", "companyId")
            }
        )
        assertInstanceOf(PushDelivery.Background::class.java, sent.delivery)
        assertEquals(1L, requireNotNull(f.pushHead()).deliveredRevision)
    }

    @Test
    fun expiredMatrixBearerDoesNotBlockDurablePushHandoffOrAcquireACredential() {
        val f = SpaceReadinessFanoutFixture(postgres, pushPublication = true)
        f.rotatePeer(expiresAt = f.now().plusSeconds(2))
        val lease = f.claimPush()
        val providers =
            f.e.s.owner.queryForList(
                "select * from collaboration_space_provider_sessions order by device_id"
            )
        f.success(
            f.command(service = enabled, clock = SpaceGatewayClock(f.now().plusSeconds(3)))
                .fanOut(lease)
        )
        assertNotNull(f.pushHead())
        assertTrue(f.deliveries.isEmpty())
        assertEquals(
            providers,
            f.e.s.owner.queryForList(
                "select * from collaboration_space_provider_sessions order by device_id"
            ),
        )
        assertNull(
            f.success(
                f.e.workerTransactions.run(f.e.peerActor) {
                    f.nativePush.find(f.e.peer, f.e.peerParent)
                }
            ),
            "Handoff does not issue or require an FCM token",
        )
    }

    @Test
    fun postAppendFailureCancellationDeadlineAndDirectoryLossRollBackBothQueues() {
        val f = SpaceReadinessFanoutFixture(postgres, pushPublication = true)
        val lease = f.claimPush()
        val original = requireNotNull(f.checkpoint(lease))
        for (mode in listOf("failure", "cancel", "deadline", "directory")) {
            var appended = false
            val clock = SpaceGatewayClock(f.now())
            val pushes =
                object : CollaborationSpacePushQueueRepository by f.pushQueue {
                    override fun append(
                        binding: CollaborationSpacePushBinding,
                        event: CollaborationPushEvent?,
                        lifetimeSeconds: Int,
                    ): Result<Boolean> =
                        f.pushQueue.append(binding, event, lifetimeSeconds).flatMap {
                            assertTrue(it)
                            appended = true
                            when (mode) {
                                "failure" ->
                                    Result.Failed(
                                        Failure(FailureKind.UNAVAILABLE, "fixture_after_handoff")
                                    )
                                "cancel" -> throw CancellationException("fixture after handoff")
                                "deadline" -> {
                                    clock.at = clock.at.plusSeconds(21)
                                    Result.Success(it)
                                }
                                else -> {
                                    f.e.revokePeer()
                                    Result.Success(it)
                                }
                            }
                        }
                }
            val command = f.command(service = enabled, clock = clock, pushQueue = pushes)
            if (mode == "cancel")
                assertThrows(CancellationException::class.java) { command.fanOut(lease) }
            else assertInstanceOf(Result.Failed::class.java, command.fanOut(lease), mode)
            assertTrue(appended, mode)
            assertNull(f.pushHead(), mode)
            assertEquals(original, f.checkpoint(lease), mode)
        }
        f.e.grantPeer()
        f.success(f.command(service = enabled).fanOut(lease))
        assertEquals(1L, requireNotNull(f.pushHead()).revision)
        assertTrue(f.deliveries.isEmpty())
    }

    @Test
    fun failedCursorWriteCannotCommitAQuietWake() {
        val f = SpaceReadinessFanoutFixture(postgres, pushPublication = true)
        val lease = f.claimPush()
        val original = requireNotNull(f.checkpoint(lease))
        var wrote = false
        val queue =
            object : CollaborationSpaceReadinessQueueRepository by f.queue {
                override fun advance(
                    checkpoint: CollaborationSpaceReadinessCheckpoint,
                    next: CollaborationReadinessPosition,
                ): Result<Boolean> =
                    f.queue.advance(checkpoint, next).flatMap {
                        assertTrue(it)
                        wrote = true
                        Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_after_cursor"))
                    }
            }
        assertInstanceOf(
            Result.Failed::class.java,
            f.command(service = enabled, queue = queue).fanOut(lease),
        )
        assertTrue(wrote)
        assertNull(f.pushHead())
        assertEquals(original, f.checkpoint(lease))
    }

    @Test
    fun missingPushBindingCompletesOnlyTheQuietChannelWithoutInventingARegistration() {
        val f = SpaceReadinessFanoutFixture(postgres)
        val matrix = f.rows().single { it["channel"] == "MATRIX" }
        val lease = f.claimPush()
        f.success(f.command(service = enabled).fanOut(lease))
        assertNotNull(f.rows().single { it["channel"] == "PUSH" }["completed_at"])
        assertNull(f.pushHead())
        assertEquals(matrix, f.rows().single { it["channel"] == "MATRIX" })
        assertTrue(f.deliveries.isEmpty())
    }

    @Test
    fun unpublishedLateOrForeignBindingsNeverAppendBeforeSuppression() {
        val f = SpaceReadinessFanoutFixture(postgres, pushPublication = true)
        val lease = f.claimPush()
        val original = requireNotNull(f.checkpoint(lease))
        val binding =
            requireNotNull(
                f.success(
                    f.e.workerTransactions.run(f.e.peerActor) {
                        f.pushBindings.find(f.e.s.space, f.e.peer, f.e.peerDevice)
                    }
                )
            )
        for (changed in
            listOf(
                binding.copy(version = 0, publishedAt = null),
                binding.copy(publishedAt = original.capturedAt.plusNanos(1000)),
                binding.copy(nativeSessionId = UUID.randomUUID()),
                binding.copy(spaceId = UUID.randomUUID()),
                binding.copy(
                    matrixDeviceId = "HRISG_${UUID.randomUUID().toString().replace("-", "")}"
                ),
            )) {
            val bindings =
                object : CollaborationSpacePushBindingRepository by f.pushBindings {
                    override fun find(
                        spaceId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                    ): Result<CollaborationSpacePushBinding?> = Result.Success(changed)
                }
            var suppressing = false
            val queue =
                object : CollaborationSpaceReadinessQueueRepository by f.queue {
                    override fun advance(
                        checkpoint: CollaborationSpaceReadinessCheckpoint,
                        next: CollaborationReadinessPosition,
                    ): Result<Boolean> {
                        assertNull(
                            f.success(f.pushQueue.find(f.e.s.space, f.e.peer, f.e.peerDevice))
                        )
                        suppressing = true
                        return Result.Failed(
                            Failure(FailureKind.UNAVAILABLE, "fixture_keep_lease_for_next_case")
                        )
                    }
                }
            assertInstanceOf(
                Result.Failed::class.java,
                f.command(service = enabled, queue = queue, pushBindings = bindings).fanOut(lease),
            )
            assertTrue(suppressing)
            assertNull(f.pushHead())
            assertEquals(original, f.checkpoint(lease))
        }
    }

    @Test
    fun quietHandoffCannotBeConsumedByAnOlderActiveFcmLease() {
        val f = SpaceReadinessFanoutFixture(postgres, pushPublication = true)
        f.registerPush()
        f.success(
            f.e.workerTransactions.run(f.e.peerActor) {
                f.pushBindings.find(f.e.s.space, f.e.peer, f.e.peerDevice).flatMap {
                    f.pushQueue.append(requireNotNull(it), null, 300)
                }
            }
        )
        val calls = mutableListOf<PushMessage>()
        val delivery =
            f.delivery(
                PushRepository {
                    calls += it
                    Result.Success(PushOutcome.ACCEPTED)
                }
            )
        val old = f.success(delivery.claim(UUID.randomUUID(), 1)).single()
        val lease = f.claimPush()
        f.success(f.command(service = enabled).fanOut(lease))
        assertEquals(old.revision + 1, requireNotNull(f.pushHead()).revision)
        f.success(delivery.deliver(old))
        assertEquals(old.revision, requireNotNull(f.pushHead()).deliveredRevision)
        val next = f.success(delivery.claim(UUID.randomUUID(), 1)).single()
        f.success(delivery.deliver(next))
        assertEquals(next.revision, requireNotNull(f.pushHead()).deliveredRevision)
        assertEquals(2, calls.size)
        assertTrue(calls.all { it.delivery is PushDelivery.Background })
        assertInstanceOf(Result.Failed::class.java, f.command(service = enabled).fanOut(lease))
        assertEquals(next.revision, requireNotNull(f.pushHead()).revision)
    }

    @Test
    fun pendingQuietWakeAlreadyCoversReadinessWithoutCreatingAnotherRevision() {
        val f = SpaceReadinessFanoutFixture(postgres, pushPublication = true)
        f.success(
            f.e.workerTransactions.run(f.e.peerActor) {
                f.pushBindings.find(f.e.s.space, f.e.peer, f.e.peerDevice).flatMap {
                    f.pushQueue.append(requireNotNull(it), null, 300)
                }
            }
        )
        val pending = requireNotNull(f.pushHead())
        f.success(f.command(service = enabled).fanOut(f.claimPush()))
        assertEquals(pending, f.pushHead())
        assertNotNull(f.rows().single { it["channel"] == "PUSH" }["completed_at"])
    }

    @Test
    fun revocationAfterHandoffIsRecheckedByTheFcmDeliveryOwner() {
        val f = SpaceReadinessFanoutFixture(postgres, pushPublication = true)
        f.registerPush()
        f.success(f.command(service = enabled).fanOut(f.claimPush()))
        val delivery = f.delivery(PushRepository { error("Revoked target must never reach FCM") })
        val lease = f.success(delivery.claim(UUID.randomUUID(), 1)).single()
        f.e.s.owner.update(
            "update native_sessions set revoked_at=now(),version=version+1 where id=?",
            f.e.peerParent,
        )
        f.success(delivery.deliver(lease))
        assertEquals(lease.revision, requireNotNull(f.pushHead()).deliveredRevision)
    }

    @Test
    fun disabledPushDoesNotClaimOrConsumeEitherChannel() {
        val f = SpaceReadinessFanoutFixture(postgres, pushPublication = true)
        val before = f.rows()
        assertTrue(
            f.success(
                    f.command().claim(CollaborationSpaceReadinessChannel.PUSH, UUID.randomUUID(), 1)
                )
                .isEmpty()
        )
        assertEquals(before, f.rows())
        val lease = f.claimPush()
        val checkpoint = f.checkpoint(lease)
        assertInstanceOf(Result.Failed::class.java, f.command().fanOut(lease))
        assertEquals(checkpoint, f.checkpoint(lease))
        assertNull(f.pushHead())
        assertTrue(f.deliveries.isEmpty())
    }
}
