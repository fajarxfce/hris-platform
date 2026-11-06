package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceGatewayBootstrapTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun malformedCredentialResponsesRollBackBeforeAnyReceiptOrTokenCanBePublished() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val empty = f.counts()
        for (mode in
            listOf(
                "id",
                "version",
                "access-prefix",
                "refresh-prefix",
                "access-time",
                "hard-time",
                "late-replay",
                "expired-replay",
            )) {
            var created = false
            val gateway =
                object : CollaborationSpaceGatewaySessionRepository by f.storage.repo {
                    override fun create(
                        session: CollaborationSpaceGatewaySession,
                        operationId: UUID,
                        replayUntil: Instant,
                    ): Result<CollaborationSpaceGatewayTokens> =
                        f.storage.repo.create(session, operationId, replayUntil).map { token ->
                            created = true
                            CollaborationSpaceGatewayTokens(
                                if (mode == "id") UUID.randomUUID() else token.sessionId,
                                if (mode == "version") token.version + 1 else token.version,
                                if (mode == "access-prefix") "hris_mx_a_" + "x".repeat(43)
                                else token.accessToken,
                                if (mode == "refresh-prefix") "hris_mx_r_" + "x".repeat(43)
                                else token.refreshToken,
                                if (mode == "access-time") token.accessExpiresAt.plusSeconds(1)
                                else token.accessExpiresAt,
                                if (mode == "hard-time") token.expiresAt.plusSeconds(1)
                                else token.expiresAt,
                                when (mode) {
                                    "late-replay" -> f.clock.at.plusSeconds(31)
                                    "expired-replay" -> f.clock.at
                                    else -> token.replayUntil
                                },
                            )
                        }
                }
            assertInstanceOf(
                Result.Failed::class.java,
                f.bootstrap(command = f.command(gateway = gateway)),
            )
            assertTrue(created, mode)
            assertEquals(empty, f.counts(), mode)
        }
    }

    @Test
    fun ordinaryEmployeeBootstrapRecoversTheSamePairAcrossNativeRefreshAndRetainsItsHardDeadline() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val op = UUID.randomUUID()
        val envelope = f.storage.success(f.bootstrapEnvelope(op))
        val first = envelope.tokens
        assertEquals(f.storage.space, envelope.spaceId)
        assertEquals(f.storage.actor.accountId, envelope.accountId)
        assertEquals(f.storage.device, envelope.deviceId)
        assertEquals(f.storage.parent, envelope.nativeSessionId)
        val identity =
            f.storage.owner.queryForMap(
                "select matrix_user_id,matrix_device_id from collaboration_space_devices where id=?",
                f.storage.device,
            )
        assertEquals(identity["matrix_user_id"], envelope.matrixUserId)
        assertEquals(identity["matrix_device_id"], envelope.matrixDeviceId)
        assertEquals("CollaborationSpaceGatewayBootstrap(<redacted>)", envelope.toString())
        assertEquals(
            mapOf(
                "collaboration_space_gateway_sessions" to 1,
                "operation_receipts" to 1,
                "audit_entries" to 1,
                "outbox_events" to 1,
            ),
            f.counts(),
        )
        f.clock.at = f.clock.at.plusSeconds(1)
        f.storage.owner.update(
            "update native_sessions set version=version+1,rotated_at=?::timestamptz where id=?",
            f.clock.at.toString(),
            f.storage.parent,
        )
        val replay = f.storage.success(f.bootstrap(op, nativeVersion = 1))
        assertEquals(first.accessToken, replay.accessToken)
        assertEquals(first.refreshToken, replay.refreshToken)
        assertEquals(first.replayUntil, replay.replayUntil)
        assertEquals(1, f.counts()["operation_receipts"])
        assertInstanceOf(
            Result.Failed::class.java,
            f.bootstrap(op, nativeVersion = 1, build = f.client.copy(build = 6)),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.bootstrap(expectedGatewayVersion = 0, nativeVersion = 1),
        )
        f.clock.at = f.clock.at.plusSeconds(10)
        val rotated =
            f.storage.success(
                f.bootstrap(
                    expectedGatewayVersion = 0,
                    nativeVersion = 1,
                    build = f.client.copy(build = 6),
                )
            )
        assertEquals(1L, rotated.version)
        assertEquals(first.expiresAt, rotated.expiresAt)
        assertNotEquals(first.accessToken, rotated.accessToken)
        assertEquals(6, f.storage.raw().clientBuild)
        assertEquals(2, f.counts()["operation_receipts"])
        assertEquals(2, f.counts()["audit_entries"])
        assertEquals(2, f.counts()["outbox_events"])
    }

    @Test
    fun companyActorsForeignAccountsStaleNativeProofAndDisabledOrOldClientsCannotIssueCredentials() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val empty = f.counts()
        for (result in
            listOf(
                f.bootstrap(request = f.storage.actor.copy(companyId = f.storage.company)),
                f.bootstrap(request = f.storage.actor.copy(accountId = UUID.randomUUID())),
                f.bootstrap(nativeVersion = 1),
                f.bootstrap(build = null),
                f.bootstrap(build = f.client.copy(build = 4)),
                f.bootstrap(command = f.command(policy = CollaborationSpacePolicy())),
            )) assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(empty, f.counts())
        f.storage.owner.update(
            "update company_memberships set active=false where account_id=?",
            f.storage.account,
        )
        assertInstanceOf(Result.Failed::class.java, f.bootstrap())
        assertEquals(empty, f.counts())
    }

    @Test
    fun missingForeignExpiredOrRetiredProviderMetadataCannotAuthorizeBootstrap() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val empty = f.counts()
        val mutations =
            listOf<(CollaborationSpaceProviderState?) -> CollaborationSpaceProviderState?>(
                { null },
                { it!!.copy(spaceId = UUID.randomUUID()) },
                { it!!.copy(accountId = UUID.randomUUID()) },
                { it!!.copy(deviceId = UUID.randomUUID()) },
                { it!!.copy(nativeSessionId = UUID.randomUUID()) },
                { it!!.copy(matrixUserId = "@foreign:matrix.example.test") },
                { it!!.copy(matrixDeviceId = "FOREIGN") },
                { it!!.copy(expiresAt = f.clock.at) },
                { it!!.copy(clearedAt = f.clock.at) },
            )
        for (change in mutations) {
            val provider =
                object : CollaborationSpaceProviderSessionRepository by f.providers {
                    override fun metadata(
                        spaceId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                    ): Result<CollaborationSpaceProviderState?> =
                        f.providers.metadata(spaceId, accountId, deviceId).map(change)
                }
            assertInstanceOf(
                Result.Failed::class.java,
                f.bootstrap(command = f.command(providers = provider)),
            )
            assertEquals(empty, f.counts())
        }
    }

    @Test
    fun receiptJournalLateAdmissionAndCancellationFailuresRollBackActualCredentialWrites() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val empty = f.counts()
        val at = f.clock.at
        for (mode in
            listOf(
                "receipt",
                "journal",
                "group",
                "budget",
                "backward",
                "cancel",
                "interrupt",
                "flag",
            )) {
            var unavailable = false
            var recorded = false
            f.clock.at = at
            val operations =
                object : OperationRepository by f.operations {
                    override fun record(
                        actor: Actor,
                        key: OperationKey,
                        receipt: MutationReceipt,
                    ): Result<Unit> =
                        f.operations.record(actor, key, receipt).flatMap {
                            if (mode == "receipt") {
                                recorded = true
                                rejected()
                            } else Result.Success(Unit)
                        }
                }
            val journal =
                object : ChangeJournalRepository {
                    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                        f.journal.record(actor, change).flatMap {
                            recorded = true
                            when (mode) {
                                "journal" -> rejected()
                                "cancel" -> throw CancellationException("fixture cancellation")
                                "interrupt" -> throw InterruptedException("fixture interrupt")
                                else -> {
                                    when (mode) {
                                        "group" -> unavailable = true
                                        "budget" -> f.clock.at = at.plusSeconds(30)
                                        "backward" -> f.clock.at = at.minusSeconds(1)
                                        "flag" -> Thread.currentThread().interrupt()
                                    }
                                    Result.Success(Unit)
                                }
                            }
                        }
                }
            val spaces =
                object : CollaborationSpaceRepository by f.spaces {
                    override fun current(): Result<UUID?> =
                        if (unavailable) Result.Success(null) else f.spaces.current()
                }
            val command = f.command(operations = operations, journal = journal, spaces = spaces)
            try {
                when (mode) {
                    "cancel" ->
                        assertThrows(CancellationException::class.java) {
                            f.bootstrap(command = command)
                        }
                    "interrupt",
                    "flag" ->
                        assertThrows(InterruptedException::class.java) {
                            f.bootstrap(command = command)
                        }
                    else ->
                        assertInstanceOf(Result.Failed::class.java, f.bootstrap(command = command))
                }
            } finally {
                Thread.interrupted()
            }
            assertTrue(recorded, mode)
            assertEquals(empty, f.counts(), mode)
        }
    }

    @Test
    fun competingCopiesOfTheSameOperationReturnOneCommittedResponse() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val operation = UUID.randomUUID()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val jobs =
                (1..2).map {
                    pool.submit<Result<CollaborationSpaceGatewayTokens>> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        f.bootstrap(operation)
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val pairs = jobs.map { f.storage.success(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(pairs[0].accessToken, pairs[1].accessToken)
            assertEquals(pairs[0].refreshToken, pairs[1].refreshToken)
            assertEquals(1, f.counts()["operation_receipts"])
            assertEquals(1, f.counts()["audit_entries"])
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun rejected() = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_write_failed"))
}
