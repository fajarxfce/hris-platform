package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpacePushKey
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.AccessNativeCollaborationSpaceMatrixDevice
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.NativePushRegistration
import dev.fajar.hris.identity.domain.entities.PushPlatform
import dev.fajar.hris.identity.domain.repositories.NativePushRegistrationRepository
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/** Actual runtime RLS and encrypted storage, with bounded provider outcome/race injection. */
@Testcontainers
class NativeCollaborationSpacePusherTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun registrationAndBothDeploymentFlagsAreRequiredBeforeReservingOrCallingMatrix() {
        val f = Fixture()
        assertEquals("collaboration_push_unavailable", failed(f.ensure()))
        f.save()
        for (service in
            listOf(
                CollaborationServicePolicy(),
                CollaborationServicePolicy(true),
                CollaborationServicePolicy(false, true),
            )) {
            assertEquals(
                "collaboration_push_unavailable",
                failed(f.ensure(f.command(service = service))),
            )
        }
        assertNull(f.binding())
        assertTrue(f.calls.isEmpty())
        assertEquals(0, f.f.leases())
        assertEquals(0, f.journal("reserved"))
    }

    @Test
    fun uncertainProviderResultAndRegistrationRotationReuseOneImmutableCapability() {
        val f = Fixture()
        f.save()
        f.outcome = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_uncertain"))
        assertEquals("fixture_provider_uncertain", failed(f.ensure()))
        val retained = requireNotNull(f.binding())
        assertEquals(0L, retained.version)
        assertNull(retained.publishedAt)
        f.outcome = Result.Success(Unit)
        repeat(2) { success(f.ensure()) }
        val published = requireNotNull(f.binding())
        f.save()
        success(f.ensure())
        assertEquals(published, f.binding())
        assertEquals(retained, published.copy(version = 0, publishedAt = null))
        assertEquals(4, f.calls.size)
        assertEquals(1, f.calls.toSet().size)
        assertEquals(1, f.journal("reserved"))
        assertEquals(1, f.journal("published"))
        assertEquals(0, f.f.leases())
        assertEquals(
            0,
            f.f.s.owner.queryForObject(
                "select count(*) from collaboration_push_bindings",
                Int::class.java,
            ),
        )
    }

    @Test
    fun changedNativeRegistrationWithholdsEitherProviderOutcomeAndCanRetryTheSameCapability() {
        val f = Fixture()
        f.save()
        for (fails in listOf(false, true)) {
            f.after = { f.save() }
            f.outcome =
                if (fails)
                    Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_uncertain"))
                else Result.Success(Unit)
            assertEquals("collaboration_space_push_changed", failed(f.ensure()))
            assertEquals(0L, f.binding()!!.version)
            assertEquals(0, f.journal("published"))
            assertEquals(0, f.f.leases())
        }
        f.after = {}
        f.outcome = Result.Success(Unit)
        success(f.ensure())
        assertEquals(1, f.calls.toSet().size)
        assertEquals(1L, f.binding()!!.version)
    }

    @Test
    fun disabledOrExpiredRegistrationCannotPublishAndReenableKeepsTheSameIdentity() {
        val f = Fixture()
        f.save()
        f.after = { f.save(enabled = false) }
        assertEquals("collaboration_push_unavailable", failed(f.ensure()))
        val pending = f.binding()
        assertEquals("collaboration_push_unavailable", failed(f.ensure()))
        assertEquals(1, f.calls.size)
        f.save(expiresAt = f.now().minusSeconds(1))
        assertEquals("collaboration_push_unavailable", failed(f.ensure()))
        assertEquals(pending, f.binding())
        f.after = {}
        f.save()
        success(f.ensure())
        assertEquals(1, f.calls.toSet().size)
        assertEquals(0, f.f.leases())
    }

    @Test fun nativeRevocationWithholdsSuccessfulProviderRegistration() = revoked(false)

    @Test fun nativeRevocationTakesPrecedenceOverFailedProviderRegistration() = revoked(true)

    private fun revoked(providerFails: Boolean) {
        val f = Fixture()
        f.save()
        f.outcome =
            if (providerFails)
                Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_uncertain"))
            else Result.Success(Unit)
        f.after = {
            f.f.s.owner.update(
                "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
                f.f.s.parent,
            )
        }
        assertEquals(FailureKind.UNAUTHENTICATED, (f.ensure() as Result.Failed).failure.kind)
        assertEquals(0L, f.binding()!!.version)
        assertEquals(0, f.journal("published"))
        assertEquals(0, f.f.leases())
        assertEquals(FailureKind.UNAUTHENTICATED, (f.ensure() as Result.Failed).failure.kind)
        assertEquals(1, f.calls.size)
    }

    @Test
    fun cancellationInterruptionAndLateProviderCompletionReleaseOwnershipWithoutPublication() {
        val f = Fixture()
        f.save()
        f.after = { throw CancellationException("fictional cancellation") }
        assertThrows(CancellationException::class.java) { f.ensure() }
        assertEquals(0, f.f.leases())
        f.after = { throw InterruptedException("fictional interruption") }
        assertThrows(InterruptedException::class.java) { f.ensure() }
        assertEquals(0, f.f.leases())
        f.after = { f.clock.offset = 31 }
        assertEquals("collaboration_admission_changed", failed(f.ensure()))
        assertEquals(0L, f.binding()!!.version)
        assertEquals(0, f.journal("published"))
        assertEquals(0, f.f.leases())
        f.clock.offset = 0
        f.after = {}
        success(f.ensure())
        assertEquals(1, f.calls.toSet().size)
    }

    @Test
    fun registrationExpiryBoundsTheProviderDeadlineAndIsCheckedAfterItsReply() {
        val f = Fixture()
        val expires = f.now().plusSeconds(12)
        f.save(expiresAt = expires)
        f.after = { f.clock.offset = 13 }
        assertEquals("collaboration_push_unavailable", failed(f.ensure()))
        assertEquals(listOf(expires), f.deadlines.toList())
        assertEquals(0L, f.binding()!!.version)
        assertEquals(0, f.f.leases())
    }

    @Test
    fun actualJournalFailureRollsBackReservationAndPublicationWithTheirAuditAndLeaseWrites() {
        val f = Fixture()
        f.save()
        var reject = "reserved"
        val journal =
            object : ChangeJournalRepository by f.f.base.journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    f.f.base.journal.record(actor, change).flatMap {
                        if (change.action == "collaboration.space_push_$reject")
                            Result.Failed(
                                Failure(FailureKind.UNAVAILABLE, "fixture_journal_failed")
                            )
                        else Result.Success(Unit)
                    }
            }
        val command = f.command(journal = journal)
        assertEquals("fixture_journal_failed", failed(f.ensure(command)))
        assertNull(f.binding())
        assertTrue(f.calls.isEmpty())
        assertEquals(0, f.journal("reserved"))
        assertEquals(0, f.f.leases())
        reject = "published"
        assertEquals("fixture_journal_failed", failed(f.ensure(command)))
        assertEquals(0L, f.binding()!!.version)
        assertEquals(1, f.journal("reserved"))
        assertEquals(0, f.journal("published"))
        assertEquals(0, f.f.leases())
        reject = "none"
        success(f.ensure(command))
        assertEquals(1, f.journal("published"))
        assertEquals(1, f.calls.toSet().size)
    }

    @Test
    fun publicationReadbackMismatchRollsBackTheAcknowledgementAndItsJournal() {
        val f = Fixture()
        f.save()
        var changed = false
        val bindings =
            object : CollaborationSpacePushBindingRepository by f.f.pushBindings {
                override fun publish(
                    expected: CollaborationSpacePushBinding,
                    at: Instant,
                ): Result<Unit> = f.f.pushBindings.publish(expected, at).map { changed = true }

                override fun find(
                    spaceId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                ): Result<CollaborationSpacePushBinding?> =
                    f.f.pushBindings.find(spaceId, accountId, deviceId).map {
                        if (changed)
                            it?.copy(
                                matrixDeviceId =
                                    "HRISG_${UUID.randomUUID().toString().replace("-", "")}"
                            )
                        else it
                    }
            }
        assertEquals(
            "collaboration_space_push_changed",
            failed(f.ensure(f.command(bindings = bindings))),
        )
        assertEquals(0L, f.binding()!!.version)
        assertEquals(1, f.journal("reserved"))
        assertEquals(0, f.journal("published"))
        assertEquals(0, f.f.leases())
        success(f.ensure())
        assertEquals(1, f.calls.toSet().size)
    }

    @Test
    fun retryRejectsAChangedAlreadyPublishedTimestampAfterProviderIo() {
        val f = Fixture()
        f.save()
        success(f.ensure())
        val published = requireNotNull(f.binding())
        assertTrue(published.publishedAt!! > published.reservedAt)
        var changed = false
        val bindings =
            object : CollaborationSpacePushBindingRepository by f.f.pushBindings {
                override fun find(
                    spaceId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                ): Result<CollaborationSpacePushBinding?> =
                    f.f.pushBindings.find(spaceId, accountId, deviceId).map {
                        if (changed) it?.copy(publishedAt = it.publishedAt?.minusNanos(1000))
                        else it
                    }
            }
        f.after = { changed = true }
        assertEquals(
            "collaboration_space_push_changed",
            failed(f.ensure(f.command(bindings = bindings))),
        )
        assertEquals(published, f.binding())
        assertEquals(1, f.journal("published"))
        assertEquals(0, f.f.leases())
    }

    @Test
    fun concurrentRequestsShareOneReservationAndPublishItOnlyOnce() {
        val f = Fixture()
        f.save()
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        f.after = {
            entered.countDown()
            assertTrue(release.await(10, TimeUnit.SECONDS))
        }
        try {
            val requests = List(2) { pool.submit<Result<Unit>> { f.ensure() } }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            assertEquals(0L, f.binding()!!.version)
            assertEquals(1, f.journal("reserved"))
            release.countDown()
            requests.forEach { success(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(2, f.calls.size)
            assertEquals(1, f.calls.toSet().size)
            assertEquals(1L, f.binding()!!.version)
            assertEquals(1, f.journal("published"))
            assertEquals(0, f.f.leases())
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private inner class Fixture {
        val f = SpaceKeyExchangeFixture(postgres)
        val clock = OffsetClock()
        val calls = CopyOnWriteArrayList<String>()
        val deadlines = CopyOnWriteArrayList<Instant>()
        var after: () -> Unit = {}
        var outcome: Result<Unit> = Result.Success(Unit)
        private var registration: NativePushRegistration? = null

        init {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .target("136")
                .load()
                .migrate()
            f.s.owner.execute(
                "grant select,insert,update on collaboration_space_push_bindings to space_gateway_fixture"
            )
        }

        fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

        fun save(enabled: Boolean = true, expiresAt: Instant = now().plusSeconds(120)) {
            val previous = registration
            val value =
                NativePushRegistration(
                    f.s.parent,
                    f.s.account,
                    PushPlatform.ANDROID,
                    enabled,
                    previous?.version?.plus(1) ?: 0,
                    previous?.registeredAt ?: f.s.at,
                    now(),
                    expiresAt,
                )
            assertTrue(
                success(
                    f.s.tx {
                        f.nativePush.save(
                            value,
                            if (enabled) "fictional-native-token-${value.version}" else null,
                            previous?.version,
                        )
                    }
                )
            )
            registration = value
        }

        private val metadataOnly =
            object : NativePushRegistrationRepository by f.nativePush {
                override fun token(
                    accountId: UUID,
                    sessionId: UUID,
                    expectedVersion: Long,
                ): Result<String?> =
                    error("Managed Matrix pusher must never acquire a native push token")
            }

        private val provider =
            object : CollaborationMatrixPusherRepository {
                override fun set(pushKey: String, bearer: String, deadline: Instant): Result<Unit> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
                    assertEquals("fixture-owned-query-token", bearer)
                    assertTrue(validCollaborationSpacePushKey(pushKey))
                    assertTrue(
                        deadline > Instant.now() && deadline <= Instant.now().plusSeconds(30)
                    )
                    assertTrue(f.leases() in 1..7)
                    val retained = requireNotNull(binding())
                    assertEquals(pushKey, success(f.s.tx { f.pushBindings.key(retained) })!!.value)
                    calls += pushKey
                    deadlines += deadline
                    after()
                    return outcome
                }

                override fun remove(
                    pushKey: String,
                    bearer: String,
                    deadline: Instant,
                ): Result<Unit> =
                    error("Native ensure never removes or replaces an uncertain pusher")
            }

        fun command(
            service: CollaborationServicePolicy = CollaborationServicePolicy(true, true),
            journal: ChangeJournalRepository = f.base.journal,
            bindings: CollaborationSpacePushBindingRepository = f.pushBindings,
        ) =
            f.command(
                f.provider(),
                clock = clock,
                service = service,
                pushBindings = bindings,
                providerPush = provider,
                nativePush = metadataOnly,
                journal = journal,
            )

        fun ensure(command: AccessNativeCollaborationSpaceMatrixDevice = command()) =
            command.ensurePusher(f.tokens.accessToken, UUID.randomUUID())

        fun binding() = success(f.s.tx { f.pushBindings.find(f.s.space, f.s.account, f.s.device) })

        fun journal(phase: String) =
            f.s.owner.queryForObject(
                "select count(*) from audit_entries where action=? and resource_id=?",
                Int::class.java,
                "collaboration.space_push_$phase",
                f.s.device,
            )!!
    }

    private class OffsetClock(var offset: Long = 0) : Clock() {
        override fun instant(): Instant = Instant.now().plusSeconds(offset)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private fun failed(result: Result<*>) = (result as Result.Failed).failure.code

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            result,
            (result as? Result.Failed)?.failure?.code,
        )
        return (result as Result.Success).value
    }
}
