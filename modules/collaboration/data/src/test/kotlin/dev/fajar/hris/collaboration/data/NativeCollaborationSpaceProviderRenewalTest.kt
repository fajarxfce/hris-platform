package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.RenewNativeCollaborationSpaceProvider
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.parseIdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceProviderRenewalTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun expiredProviderRenewsOutsideSqlAndReplaysAcrossNativeRefreshWithoutChangingDeviceIdentity() {
        val f = Fixture()
        val before = f.state()
        val device = f.deviceRows()
        val operation = UUID.randomUUID()
        val renewed = success(f.renew(operation))
        assertEquals(CollaborationSpaceProviderRenewal(f.s.device, before.generation + 1), renewed)
        assertEquals(1, f.logins.get())
        val after = f.state()
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.matrixDeviceId, after.matrixDeviceId)
        assertEquals(before.matrixUserId, after.matrixUserId)
        assertEquals(device, f.deviceRows())
        assertTrue(after.expiresAt > Instant.now())
        assertEquals(
            "fixture-renewed-token",
            success(f.s.tx { f.providers.credential(after) }).accessToken,
        )
        assertEquals(0, f.leases())
        assertEquals(1, f.receipts())
        assertEquals(1, f.audits())
        assertEquals(renewed, success(f.renew(operation)))
        f.s.owner.update("update native_sessions set version=version+1 where id=?", f.s.parent)
        assertEquals(renewed, success(f.renew(operation, nativeVersion = 1)))
        assertEquals(renewed, success(f.renew(nativeVersion = 1)))
        assertEquals(1, f.logins.get())
        assertEquals(2, f.receipts())
        assertEquals(1, f.audits())
        f.base.clock.at = Instant.now()
        assertInstanceOf(
            Result.Success::class.java,
            f.base.bootstrap(nativeVersion = 1, command = f.base.command(providers = f.providers)),
        )
        val expiredClock = Clock.fixed(after.expiresAt, ZoneOffset.UTC)
        failure(
            f.renew(operation, nativeVersion = 1, command = f.command(clock = expiredClock)),
            "collaboration_space_provider_renewal_required",
        )
        assertEquals(1, f.logins.get())
    }

    @Test
    fun simultaneousRenewalsKeepOneProviderCandidateAndLeaveOrdinaryGatewayCapacity() {
        val f = Fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val provider =
                f.matrix {
                    entered.countDown()
                    assertTrue(release.await(15, TimeUnit.SECONDS))
                    Result.Success(f.candidate())
                }
            val first =
                pool.submit<Result<CollaborationSpaceProviderRenewal>> {
                    f.renew(command = f.command(matrix = provider))
                }
            assertTrue(entered.await(15, TimeUnit.SECONDS))
            failure(f.renew(), "collaboration_space_provider_busy")
            assertEquals(1, f.logins.get())
            // The reserved native renewal must not consume an ordinary gateway slot.
            val gateway =
                success(
                    f.s.tx {
                        f.s.repo.create(f.s.session, UUID.randomUUID(), f.s.at.plusSeconds(30))
                    }
                )
            val position =
                CollaborationSpaceRequestPosition(
                    f.owner,
                    CollaborationSpaceRequestKind.GATEWAY,
                    gateway.sessionId,
                    gateway.version,
                )
            val ordinary =
                success(
                    f.s.tx {
                        f.requests.claim(
                            position,
                            Instant.now().plusSeconds(15).truncatedTo(ChronoUnit.MICROS),
                        )
                    }
                )
            assertEquals(1, requireNotNull(ordinary).slot)
            assertTrue(success(f.s.tx { f.requests.release(ordinary) }))
            release.countDown()
            assertInstanceOf(Result.Success::class.java, first.get(20, TimeUnit.SECONDS))
            assertEquals(0, f.leases())
            assertEquals(1, f.logins.get())
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun failedProviderAndPostIoAdmissionDenialReleaseWithoutPublishingCredentialOrReceipt() {
        val f = Fixture()
        val before = f.state()
        for (succeeds in listOf(false, true)) {
            var deny = false
            val spaces =
                object : CollaborationSpaceRepository by f.base.spaces {
                    override fun current(): Result<UUID?> =
                        if (deny) Result.Success(null) else f.base.spaces.current()
                }
            val matrix =
                f.matrix {
                    deny = true
                    if (succeeds) Result.Success(f.candidate())
                    else Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_down"))
                }
            failure(
                f.renew(command = f.command(matrix = matrix, spaces = spaces)),
                "collaboration_access_denied",
            )
            assertEquals(before, f.state())
            assertEquals(0, f.leases())
            assertEquals(0, f.receipts())
        }
        val failed =
            f.matrix { Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_down")) }
        failure(f.renew(command = f.command(matrix = failed)), "fixture_provider_down")
        assertEquals(before, f.state())
        assertEquals(0, f.leases())
        f.s.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.s.account,
        )
        failure(f.renew(), "collaboration_access_denied")
        assertEquals(3, f.logins.get())
    }

    @Test
    fun lateWrongAndCancelledProviderCandidatesNeverChangeTheStoredGeneration() {
        val f = Fixture()
        val before = f.state()
        for (mode in
            listOf("user", "device", "token", "short", "long", "late", "cancel", "interrupt")) {
            val clock = OffsetClock()
            val matrix =
                f.matrix {
                    when (mode) {
                        "cancel" -> throw CancellationException("fixture cancellation")
                        "interrupt" -> throw InterruptedException("fixture interruption")
                        "late" -> clock.seconds = 30
                    }
                    val value = f.candidate()
                    Result.Success(
                        when (mode) {
                            "user" ->
                                changedCandidate(value, userId = "@foreign:matrix.example.test")
                            "device" -> changedCandidate(value, deviceId = "OTHER")
                            "token" -> changedCandidate(value, accessToken = "invalid token")
                            "short" ->
                                changedCandidate(value, expiresAt = Instant.now().plusSeconds(59))
                            "long" ->
                                changedCandidate(value, expiresAt = Instant.now().plusSeconds(601))
                            else -> value
                        }
                    )
                }
            val run = { f.renew(command = f.command(matrix = matrix, clock = clock)) }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { run() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { run() }
                "late" -> failure(run(), "collaboration_space_provider_changed")
                else -> failure(run(), "collaboration_provider_invalid_response")
            }
            assertEquals(before, f.state(), mode)
            assertEquals(0, f.leases(), mode)
            assertEquals(0, f.receipts(), mode)
            assertEquals(0, f.audits(), mode)
        }
    }

    @Test
    fun providerReceiptAndJournalWritesRollBackOnLateFailureCancellationAndReadbackMismatch() {
        val f = Fixture()
        val before = f.state()
        for (mode in listOf("receipt", "journal", "cancel", "interrupt", "readback", "admission")) {
            var wrote = false
            val providers =
                object : CollaborationSpaceProviderSessionRepository by f.providers {
                    override fun rotate(
                        session: CollaborationSpaceProviderSession,
                        expectedGeneration: Long,
                    ): Result<Unit> =
                        f.providers.rotate(session, expectedGeneration).also {
                            wrote = it is Result.Success
                        }

                    override fun metadata(
                        spaceId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                    ): Result<CollaborationSpaceProviderState?> =
                        if (wrote && mode == "readback") Result.Success(before)
                        else f.providers.metadata(spaceId, accountId, deviceId)
                }
            val operations =
                object : OperationRepository by f.base.operations {
                    override fun record(
                        actor: Actor,
                        key: OperationKey,
                        receipt: MutationReceipt,
                    ): Result<Unit> =
                        f.base.operations.record(actor, key, receipt).flatMap {
                            if (mode == "receipt") rejected() else Result.Success(Unit)
                        }
                }
            val journal =
                object : ChangeJournalRepository by f.base.journal {
                    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                        f.base.journal.record(actor, change).flatMap {
                            when (mode) {
                                "journal" -> rejected()
                                "cancel" ->
                                    throw CancellationException("fixture post-write cancellation")
                                "interrupt" ->
                                    throw InterruptedException("fixture post-write interruption")
                                else -> Result.Success(Unit)
                            }
                        }
                }
            val spaces =
                object : CollaborationSpaceRepository by f.base.spaces {
                    override fun current(): Result<UUID?> =
                        if (wrote && mode == "admission") Result.Success(null)
                        else f.base.spaces.current()
                }
            val run = {
                f.renew(
                    command =
                        f.command(
                            providers = providers,
                            operations = operations,
                            journal = journal,
                            spaces = spaces,
                        )
                )
            }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { run() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { run() }
                else -> assertInstanceOf(Result.Failed::class.java, run(), mode)
            }
            assertTrue(wrote, mode)
            assertEquals(before, f.state(), mode)
            assertEquals(0, f.leases(), mode)
            assertEquals(0, f.receipts(), mode)
            assertEquals(0, f.audits(), mode)
        }
    }

    @Test
    fun lostLeaseCannotPublishOrReleaseItsSuccessorAndNativeRefreshRequiresFreshAdmission() {
        val f = Fixture()
        val before = f.state()
        var successor: CollaborationSpaceRequestLease? = null
        val replaced =
            f.matrix {
                success(
                    f.s.tx {
                        f.requests.find(f.owner, 0).flatMap { lease ->
                            f.requests
                                .release(requireNotNull(lease))
                                .flatMap {
                                    f.requests.claim(
                                        CollaborationSpaceRequestPosition(
                                            f.owner,
                                            CollaborationSpaceRequestKind.PROVIDER_RENEWAL,
                                        ),
                                        Instant.now().plusSeconds(20).truncatedTo(ChronoUnit.MICROS),
                                    )
                                }
                                .map { successor = requireNotNull(it) }
                        }
                    }
                )
                Result.Success(f.candidate())
            }
        failure(f.renew(command = f.command(matrix = replaced)), "collaboration_space_request_lost")
        assertEquals(successor, success(f.s.tx { f.requests.find(f.owner, 0) }))
        assertEquals(before, f.state())
        assertTrue(success(f.s.tx { f.requests.release(requireNotNull(successor)) }))
        val refresh =
            f.matrix {
                f.s.owner.update(
                    "update native_sessions set version=version+1 where id=?",
                    f.s.parent,
                )
                Result.Success(f.candidate())
            }
        failure(f.renew(command = f.command(matrix = refresh)), "native_session_invalid")
        assertEquals(before, f.state())
        assertEquals(0, f.leases())
        assertEquals(0, f.receipts())
        assertInstanceOf(Result.Success::class.java, f.renew(nativeVersion = 1))
    }

    private inner class Fixture {
        val base = SpaceGatewayBootstrapFixture(postgres)
        val s = base.storage
        val owner = CollaborationSpaceRequestOwner(s.space, s.account, s.device, s.parent)
        val requests =
            StoredCollaborationSpaceRequestRepository(
                PostgresCollaborationSpaceRequestDataSource(base.sql)
            )
        val providers =
            EncryptedCollaborationSpaceProviderSessionRepository(
                PostgresCollaborationSpaceProviderSessionDataSource(base.sql),
                JceIdentityTokenDataSource(
                    parseIdentityKeyring(
                        "fixture",
                        "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 61 }),
                    )
                ),
            )
        val logins = AtomicInteger()

        init {
            Flyway.configure().dataSource(s.source).target("127").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on collaboration_space_request_leases to space_gateway_fixture,space_gateway_worker"
            )
            val original = state()
            // Seed a genuinely expired token with an admitted historical rotation, keeping
            // immutable creation/native/device provenance and all database triggers active.
            success(
                s.tx {
                    providers.rotate(
                        CollaborationSpaceProviderSession(
                            original.copy(
                                generation = original.generation + 1,
                                updatedAt = s.at.plusSeconds(30),
                                expiresAt =
                                    Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MICROS),
                            ),
                            "fixture-expired-token",
                        ),
                        original.generation,
                    )
                }
            )
        }

        fun state() =
            requireNotNull(success(s.tx { providers.metadata(s.space, s.account, s.device) }))

        fun candidate() =
            state().let {
                CollaborationMatrixCredential(
                    it.matrixUserId,
                    it.matrixDeviceId,
                    "fixture-renewed-token",
                    Instant.now().plusSeconds(300),
                )
            }

        fun matrix(
            action: (CollaborationSpaceMatrixLogin) -> Result<CollaborationMatrixCredential> = {
                Result.Success(candidate())
            }
        ) =
            object : CollaborationSpaceMatrixRepository {
                override fun login(
                    request: CollaborationSpaceMatrixLogin
                ): Result<CollaborationMatrixCredential> {
                    logins.incrementAndGet()
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
                    assertEquals(1, leases())
                    assertEquals(s.space, request.spaceId)
                    assertEquals(s.account, request.accountId)
                    assertEquals(s.device, request.deviceId)
                    assertNull(request.canonicalLegacyCompanyId)
                    assertNull(request.retainedDevice)
                    assertTrue(request.expiresAt <= request.issuedAt.plusSeconds(30))
                    return action(request)
                }
            }

        fun command(
            matrix: CollaborationSpaceMatrixRepository = matrix(),
            providers: CollaborationSpaceProviderSessionRepository = this.providers,
            spaces: CollaborationSpaceRepository = base.spaces,
            operations: OperationRepository = base.operations,
            journal: ChangeJournalRepository = base.journal,
            clock: Clock = Clock.systemUTC(),
        ) =
            RenewNativeCollaborationSpaceProvider(
                requests,
                base.registry,
                providers,
                matrix,
                DisabledCollaborationMatrixKeyRepository(),
                base.selections,
                base.legacy,
                base.completions,
                base.provisioning,
                spaces,
                base.sessions,
                base.identities,
                operations,
                journal,
                s.transactions,
                IdentitySecurityPolicy(),
                CollaborationSpacePolicy(true, 5, 5),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                clock,
            )

        fun renew(
            operation: UUID = UUID.randomUUID(),
            nativeVersion: Long = 0,
            command: RenewNativeCollaborationSpaceProvider = command(),
        ) =
            command.execute(
                s.actor,
                s.parent,
                nativeVersion,
                s.space,
                operation,
                s.device,
                1,
                base.client,
            )

        fun leases() =
            s.owner.queryForObject(
                "select count(*) from collaboration_space_request_leases",
                Int::class.java,
            )!!

        fun receipts() =
            s.owner.queryForObject(
                "select count(*) from operation_receipts where operation='collaboration.space_provider_renewal'",
                Int::class.java,
            )!!

        fun audits() =
            s.owner.queryForObject(
                "select count(*) from audit_entries where action='collaboration.space_provider_renewed'",
                Int::class.java,
            )!!

        fun deviceRows() = s.owner.queryForList("select * from collaboration_space_devices")
    }

    private class OffsetClock(var seconds: Long = 0) : Clock() {
        override fun instant(): Instant = Instant.now().plusSeconds(seconds)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private fun changedCandidate(
        value: CollaborationMatrixCredential,
        userId: String = value.userId,
        deviceId: String = value.deviceId,
        accessToken: String = value.accessToken,
        expiresAt: Instant = value.expiresAt,
    ) = CollaborationMatrixCredential(userId, deviceId, accessToken, expiresAt)

    private fun rejected() = Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rejected"))

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result) {
            (result as? Result.Failed)?.failure?.code ?: "unexpected result"
        }
        return (result as Result.Success).value
    }

    private fun failure(result: Result<*>, code: String) {
        assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(code, (result as Result.Failed).failure.code)
    }
}
