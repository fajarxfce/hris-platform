package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.HandleCollaborationSpacePush
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.NativePushRegistrationRepository
import dev.fajar.hris.push.domain.entities.*
import dev.fajar.hris.push.domain.repositories.PushRepository
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Worker-capability PostgreSQL and real encrypted native registration; cloud result is injected.
 */
@Testcontainers
class CollaborationSpacePushDeliveryTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun opaqueGroupEnvelopeUsesCurrentTokenAndOnlyAcknowledgesItsCapturedRevision() {
        val f = Fixture()
        val event = f.event("first")
        f.accept(event)
        val lease = f.claim()
        success(f.command().deliver(lease))
        val sent = f.calls.single()
        assertEquals("fixture-native-0", sent.token)
        assertEquals("collaboration.space_chat_available", sent.data["eventCode"])
        assertEquals(f.f.s.space.toString(), sent.data["spaceId"])
        assertFalse(sent.data.containsKey("companyId"))
        assertEquals(event.roomId, sent.data["matrixRoomId"])
        assertEquals(event.eventId, sent.data["matrixEventId"])
        assertEquals(PushRetention.RECOVERABLE, sent.retention)
        assertInstanceOf(PushDelivery.Visible::class.java, sent.delivery)
        assertTrue(sent.dispatchDeadline <= lease.expiresAt)
        assertEquals(lease.revision, f.head().deliveredRevision)
        assertNull(f.head().lease)
        assertEquals(1, f.tokenReads)
        assertTrue(success(f.command().claim(UUID.randomUUID(), 1)).isEmpty())
    }

    @Test
    fun quietHintAndSupersededVisibleRevisionCannotInventChatContentOrReceipt() {
        val f = Fixture()
        f.accept(null)
        success(f.command().deliver(f.claim()))
        val data = f.calls.single()
        assertInstanceOf(PushDelivery.Background::class.java, data.delivery)
        assertFalse(data.data.containsKey("matrixRoomId"))
        assertFalse(data.data.containsKey("matrixEventId"))
        assertTrue(
            data.data.keys.none {
                it in setOf("content", "body", "sender", "readAt", "deliveredAt")
            }
        )
    }

    @Test
    fun callbackDuringCloudIoSurvivesOldAcknowledgmentAndIsDeliveredByItsOwnLease() {
        val f = Fixture()
        f.accept(f.event("before"))
        val old = f.claim()
        f.after = { f.accept(f.event("during")) }
        success(f.command().deliver(old))
        assertEquals(old.revision, f.head().deliveredRevision)
        assertEquals(old.revision + 1, f.head().revision)
        f.after = {}
        success(f.command().deliver(f.claim()))
        assertEquals(
            listOf("$" + "before", "$" + "during"),
            f.calls.map { it.data["matrixEventId"] },
        )
        assertEquals(f.head().revision, f.head().deliveredRevision)
    }

    @Test
    fun staleUnregisteredResultCannotDisableARotatedTokenOrConsumePendingWork() {
        val f = Fixture()
        f.accept(f.event("rotate"))
        val lease = f.claim()
        f.outcome = Result.Success(PushOutcome.UNREGISTERED)
        f.after = { f.register() }
        success(f.command().deliver(lease))
        val current = f.registration()
        assertEquals(1L, current.version)
        assertTrue(current.enabled)
        assertEquals(
            "fixture-native-1",
            success(
                f.f.s.tx {
                    f.f.nativePush.token(current.accountId, current.sessionId, current.version)
                }
            ),
        )
        assertEquals(0L, f.head().deliveredRevision)
        assertNull(f.head().lease)
        assertTrue(f.head().availableAt > Instant.now())
    }

    @Test
    fun currentUnregisteredResultAtomicallyDisablesExactTokenAndCompletesOnlyOldRevision() {
        val f = Fixture()
        f.accept(f.event("unregistered"))
        val lease = f.claim()
        f.outcome = Result.Success(PushOutcome.UNREGISTERED)
        f.after = { f.accept(f.event("newer")) }
        success(f.command().deliver(lease))
        val current = f.registration()
        assertFalse(current.enabled)
        assertEquals(1L, current.version)
        assertNull(
            success(
                f.f.s.tx {
                    f.f.nativePush.token(current.accountId, current.sessionId, current.version)
                }
            )
        )
        assertEquals(lease.revision, f.head().deliveredRevision)
        assertEquals(lease.revision + 1, f.head().revision)
        assertNull(f.head().lease)
    }

    @Test
    fun failedDisabledReadbackRollsBackActualRegistrationMutationAndQueueAcknowledgment() {
        val f = Fixture()
        f.accept(null)
        val lease = f.claim()
        f.outcome = Result.Success(PushOutcome.UNREGISTERED)
        var saved = false
        val native =
            object : NativePushRegistrationRepository by f.native {
                override fun save(
                    registration: NativePushRegistration,
                    token: String?,
                    expectedVersion: Long?,
                ): Result<Boolean> =
                    f.native.save(registration, token, expectedVersion).also { saved = true }

                override fun find(
                    accountId: UUID,
                    sessionId: UUID,
                ): Result<NativePushRegistration?> =
                    if (saved) Result.Success(null) else f.native.find(accountId, sessionId)
            }
        assertInstanceOf(Result.Failed::class.java, f.command(nativePush = native).deliver(lease))
        assertTrue(saved)
        assertTrue(f.registration().enabled)
        assertEquals(0L, f.registration().version)
        assertEquals(0L, f.head().deliveredRevision)
        assertEquals(lease, f.head().lease)
    }

    @Test
    fun finalDirectoryLossAfterDisableRollsBackBothWrites() {
        val f = Fixture()
        f.accept(null)
        val lease = f.claim()
        f.outcome = Result.Success(PushOutcome.UNREGISTERED)
        var disabled = false
        val native =
            object : NativePushRegistrationRepository by f.native {
                override fun save(
                    registration: NativePushRegistration,
                    token: String?,
                    expectedVersion: Long?,
                ): Result<Boolean> =
                    f.native.save(registration, token, expectedVersion).also { disabled = true }
            }
        val spaces =
            object : CollaborationSpaceRepository by f.f.base.spaces {
                override fun current(): Result<UUID?> =
                    if (disabled) Result.Success(null) else f.f.base.spaces.current()
            }
        assertInstanceOf(
            Result.Failed::class.java,
            f.command(nativePush = native, spaces = spaces).deliver(lease),
        )
        assertTrue(disabled)
        assertTrue(f.registration().enabled)
        assertEquals(lease, f.head().lease)
        assertEquals(0L, f.head().deliveredRevision)
    }

    @Test
    fun lateCloudResultCancellationAndInterruptionLeaveTheFencedLeaseRecoverable() {
        val f = Fixture()
        f.accept(null)
        val lease = f.claim()
        f.after = { f.clock.offset += 21 }
        assertInstanceOf(Result.Failed::class.java, f.command().deliver(lease))
        assertEquals(lease, f.head().lease)
        assertEquals(0L, f.head().deliveredRevision)
        f.clock.offset = 0
        f.after = { throw CancellationException("fictional cancellation") }
        assertThrows(CancellationException::class.java) { f.command().deliver(lease) }
        assertEquals(lease, f.head().lease)
        f.after = { throw InterruptedException("fictional interruption") }
        assertThrows(InterruptedException::class.java) { f.command().deliver(lease) }
        assertEquals(lease, f.head().lease)
    }

    @Test
    fun revokedParentBeforeDeliveryDoesNotAcquireTokenAndStaleLeaseNeverSendsTwice() {
        val f = Fixture()
        f.accept(null)
        val lease = f.claim()
        f.f.s.owner.update(
            "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
            f.f.s.parent,
        )
        success(f.command().deliver(lease))
        assertTrue(f.calls.isEmpty())
        assertEquals(0, f.tokenReads)
        assertEquals(lease.revision, f.head().deliveredRevision)
        assertInstanceOf(Result.Failed::class.java, f.command().deliver(lease))
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun retryableCloudFailureDefersInsteadOfBusyLoopingOrMarkingAReceipt() {
        val f = Fixture()
        f.accept(null)
        f.outcome =
            Result.Failed(
                Failure(
                    FailureKind.RATE_LIMITED,
                    "fixture_limited",
                    parameters = mapOf("retryAfterSeconds" to "30"),
                )
            )
        success(f.command().deliver(f.claim()))
        assertEquals(0L, f.head().deliveredRevision)
        assertNull(f.head().lease)
        assertTrue(f.head().availableAt > Instant.now().plusSeconds(20))
        assertTrue(success(f.command().claim(UUID.randomUUID(), 1)).isEmpty())
    }

    private inner class Fixture {
        val f = SpaceKeyExchangeFixture(postgres)
        val clock = OffsetClock()
        val queue =
            StoredCollaborationSpacePushQueueRepository(
                PostgresCollaborationSpacePushQueueDataSource(f.base.sql)
            )
        var tokenReads = 0
        val native =
            object : NativePushRegistrationRepository by f.nativePush {
                override fun token(
                    accountId: UUID,
                    sessionId: UUID,
                    expectedVersion: Long,
                ): Result<String?> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
                    tokenReads++
                    return f.nativePush.token(accountId, sessionId, expectedVersion)
                }
            }
        val calls = mutableListOf<PushMessage>()
        var after: () -> Unit = {}
        var outcome: Result<PushOutcome> = Result.Success(PushOutcome.ACCEPTED)
        val transport = PushRepository { message ->
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            assertTrue(message.dispatchDeadline > Instant.now())
            calls += message
            after()
            outcome
        }
        val binding =
            CollaborationSpacePushBinding(
                f.s.space,
                f.s.account,
                f.s.device,
                f.s.parent,
                f.s.user,
                "HRISG_" + f.s.device.toString().replace("-", ""),
                0,
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                null,
            )
        val key: CollaborationSpacePushKey

        init {
            Flyway.configure().dataSource(f.s.source).target("137").load().migrate()
            f.s.owner.execute("grant hris_worker_capability to space_gateway_fixture")
            f.s.owner.execute(
                "grant select,insert,update on collaboration_space_push_bindings,collaboration_space_push_hints to space_gateway_fixture"
            )
            success(f.s.tx { f.pushBindings.reserve(binding) })
            key = requireNotNull(success(f.s.tx { f.pushBindings.key(binding) }))
            register()
            f.bind()
        }

        fun registration() =
            requireNotNull(success(f.s.tx { f.nativePush.find(f.s.account, f.s.parent) }))

        fun register() {
            val old = success(f.s.tx { f.nativePush.find(f.s.account, f.s.parent) })
            val at = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val current =
                NativePushRegistration(
                    f.s.parent,
                    f.s.account,
                    PushPlatform.ANDROID,
                    true,
                    old?.version?.plus(1) ?: 0,
                    old?.registeredAt ?: at,
                    at,
                    at.plusSeconds(1800),
                )
            assertTrue(
                success(
                    f.s.tx {
                        f.nativePush.save(
                            current,
                            "fixture-native-${current.version}",
                            old?.version,
                        )
                    }
                )
            )
        }

        fun command(
            nativePush: NativePushRegistrationRepository = native,
            queue: CollaborationSpacePushQueueRepository = this.queue,
            spaces: CollaborationSpaceRepository = f.base.spaces,
        ) =
            HandleCollaborationSpacePush(
                f.pushBindings,
                queue,
                f.s.repo,
                f.base.registry,
                f.base.providers,
                f.base.selections,
                f.base.legacy,
                f.base.completions,
                f.base.provisioning,
                spaces,
                StoredCollaborationSpaceRoomRepository(
                    PostgresCollaborationSpaceRoomDataSource(f.base.sql)
                ),
                StoredCollaborationSpaceConversationRepository(
                    PostgresCollaborationSpaceConversationDataSource(f.base.sql)
                ),
                f.base.sessions,
                nativePush,
                f.base.identities,
                f.s.transactions,
                CollaborationServicePolicy(true, true),
                CollaborationSpacePolicy(true, 5, 5),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                clock,
                transport,
            )

        fun event(id: String) = CollaborationPushEvent(f.boundRoom, "$" + id)

        fun accept(event: CollaborationPushEvent?) =
            success(
                command()
                    .accept(
                        CollaborationSpacePushHint(event, listOf(key)),
                        UUID.randomUUID(),
                        clock.instant().plusSeconds(10),
                    )
            )

        fun claim() = success(command().claim(UUID.randomUUID(), 1)).single()

        fun head() =
            requireNotNull(success(f.s.tx { queue.find(f.s.space, f.s.account, f.s.device) }))
    }

    private class OffsetClock(var offset: Long = 0) : Clock() {
        override fun instant(): Instant = Instant.now().plusSeconds(offset)

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            result,
            (result as? Result.Failed)?.failure?.code,
        )
        return (result as Result.Success).value
    }
}
