package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.usecases.BootstrapNativeCollaborationSpaceGateway
import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceBootstrapSupersessionTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun initiallyAbsentOriginalCannotApplyAfterReplacementAndNoOriginalReceiptIsFabricated() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val original = intent(f)
        val operation = UUID.randomUUID()
        val result = f.storage.success(replace(f, original, operation))
        assertEquals(0L, result.tokens.version)
        assertEquals(original, result.replacement!!.superseded)
        assertEquals(operation, requireNotNull(result.replacement).replacementOperationId)
        assertEquals(f.storage.device, result.deviceId)
        assertEquals(f.storage.parent, result.nativeSessionId)
        val replay = f.storage.success(replace(f, original, operation))
        assertEquals(result.tokens.accessToken, replay.tokens.accessToken)
        assertEquals(result.tokens.refreshToken, replay.tokens.refreshToken)
        assertEquals(1, f.counts()["operation_receipts"])
        assertEquals(1, f.counts()["audit_entries"])
        assertEquals(1, f.counts()["outbox_events"])
        assertEquals(
            0,
            f.storage.owner.queryForObject(
                "select count(*) from operation_receipts where operation_id=?",
                Int::class.java,
                original.operationId,
            ),
        )
        assertChanged(f.bootstrap(original.operationId))
        assertEquals(0L, f.storage.raw().version)
        assertEquals(1, f.counts()["operation_receipts"])
        assertInstanceOf(
            Result.Failed::class.java,
            replace(f, original.copy(operationId = UUID.randomUUID()), operation),
        )
        assertEquals(0L, f.storage.raw().version)
    }

    @Test
    fun originalWaitingAfterItsOperationLockIsFencedWithoutAnAdditionalLockOrDeadlock() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val original = intent(f)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val operations =
            object : OperationRepository by f.operations {
                override fun lockAndReplay(
                    actor: Actor,
                    key: OperationKey,
                ): Result<MutationReceipt?> =
                    f.operations.lockAndReplay(actor, key).also {
                        if (key.id == original.operationId) {
                            entered.countDown()
                            check(release.await(10, TimeUnit.SECONDS))
                        }
                    }
            }
        val pool = Executors.newSingleThreadExecutor()
        try {
            val late =
                pool.submit<Result<CollaborationSpaceGatewayTokens>> {
                    f.bootstrap(original.operationId, command = f.command(operations = operations))
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val result = f.storage.success(replace(f, original))
            assertEquals(0L, result.tokens.version)
            release.countDown()
            assertChanged(late.get(5, TimeUnit.SECONDS))
            assertEquals(1, f.counts()["operation_receipts"])
            assertEquals(1, f.counts()["audit_entries"])
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun committedOriginalRequiresCurrentObservationAndExpiredReplayCanBeSupersededOnANewerBuild() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val original = intent(f)
        val first = f.storage.success(f.bootstrap(original.operationId))
        f.clock.at = f.clock.at.plusSeconds(31)
        val maintenance = f.storage.repository(f.storage.workerSource)
        val cleared =
            f.storage.transactions(f.storage.workerSource).run(
                f.storage.actor.copy(accountId = UUID(0, 0))
            ) {
                maintenance.clearExpiredReplays(f.clock.at, 100)
            }
        assertEquals(1, f.storage.success(cleared))
        assertNull(f.storage.raw().replayEncrypted)
        assertInstanceOf(Result.Failed::class.java, f.bootstrap(original.operationId))
        assertChanged(replace(f, original))
        val operation = UUID.randomUUID()
        val current = f.command(policy = CollaborationSpacePolicy(true, 6, 6))
        val oldBuild = f.bootstrap(original.operationId, command = current) as Result.Failed
        assertEquals("client_update_required", oldBuild.failure.code)
        val result =
            f.storage.success(replace(f, original, operation, observed = 0, command = current))
        assertEquals(1L, result.tokens.version)
        assertEquals(first.sessionId, result.tokens.sessionId)
        assertEquals(first.expiresAt, result.tokens.expiresAt)
        assertNotEquals(first.accessToken, result.tokens.accessToken)
        assertEquals(6, f.storage.raw().clientBuild)
        assertChanged(f.bootstrap(original.operationId))
        assertEquals(2, f.counts()["operation_receipts"])
        assertEquals(2, f.counts()["audit_entries"])
    }

    @Test
    fun repeatedLostResponsesKeepFlatOriginalCoordinatesAndFenceEveryEarlierReceipt() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val original = intent(f)
        f.storage.success(f.bootstrap(original.operationId))
        f.clock.at = f.clock.at.plusSeconds(31)
        val second = intent(f, gateway = 0, build = 6)
        val issued = f.storage.success(replace(f, original, second.operationId, observed = 0))
        f.clock.at = f.clock.at.plusSeconds(31)
        val third = UUID.randomUUID()
        val result = f.storage.success(replace(f, second, third, observed = 1, build = 7))
        assertEquals(2L, result.tokens.version)
        assertEquals(issued.tokens.sessionId, result.tokens.sessionId)
        assertEquals(issued.tokens.expiresAt, result.tokens.expiresAt)
        assertEquals(second, result.replacement!!.superseded)
        assertChanged(f.bootstrap(original.operationId))
        assertChanged(replace(f, original, second.operationId, observed = 0))
        val replay = f.storage.success(replace(f, second, third, observed = 1, build = 7))
        assertEquals(result.tokens.accessToken, replay.tokens.accessToken)
        assertEquals(result.tokens.refreshToken, replay.tokens.refreshToken)
        assertEquals(3, f.counts()["operation_receipts"])
        assertEquals(3, f.counts()["audit_entries"])
    }

    @Test
    fun competingReplacementsCommitOnlyOneHeadAndLeaveTheOriginalUnableToApply() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val original = intent(f)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val jobs =
                (1..2).map {
                    pool.submit<Result<CollaborationSpaceGatewayBootstrap>> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        replace(f, original)
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results = jobs.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(1, results.count { it is Result.Failed })
            results.filterIsInstance<Result.Failed>().forEach { assertChanged(it) }
            assertChanged(f.bootstrap(original.operationId))
            assertEquals(0L, f.storage.raw().version)
            assertEquals(1, f.counts()["operation_receipts"])
            assertEquals(1, f.counts()["audit_entries"])
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun refreshAndReplacementCannotBothPublishFromTheSameObservedHead() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val original = intent(f)
        val first = f.storage.success(f.bootstrap(original.operationId))
        f.clock.at = f.clock.at.plusSeconds(11)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val replacement =
                pool.submit<Result<CollaborationSpaceGatewayTokens>> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    replace(f, original, observed = 0).map { it.tokens }
                }
            val refresh =
                pool.submit<Result<CollaborationSpaceGatewayTokens>> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    f.refresh(first.refreshToken)
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results =
                listOf(replacement.get(10, TimeUnit.SECONDS), refresh.get(10, TimeUnit.SECONDS))
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(1, results.count { it is Result.Failed })
            assertEquals(1L, f.storage.raw().version)
            assertEquals(2, f.counts()["audit_entries"])
            assertChanged(f.bootstrap(original.operationId))
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun failedFinalAdmissionReceiptJournalOrCancellationDoesNotFenceAnUnappliedOriginal() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val original = intent(f)
        val empty = f.counts()
        val at = f.clock.at
        for (mode in listOf("receipt", "journal", "cancel", "elapsed")) {
            f.clock.at = at
            var wrote = false
            val operations =
                object : OperationRepository by f.operations {
                    override fun record(
                        actor: Actor,
                        key: OperationKey,
                        receipt: MutationReceipt,
                    ): Result<Unit> =
                        f.operations.record(actor, key, receipt).flatMap {
                            wrote = true
                            if (mode == "receipt") rejected() else Result.Success(Unit)
                        }
                }
            val journal =
                object : ChangeJournalRepository {
                    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                        f.journal.record(actor, change).flatMap {
                            when (mode) {
                                "journal" -> rejected()
                                "cancel" -> throw CancellationException("fixture cancellation")
                                else -> {
                                    if (mode == "elapsed") f.clock.at = at.plusSeconds(30)
                                    Result.Success(Unit)
                                }
                            }
                        }
                }
            val command = f.command(operations = operations, journal = journal)
            if (mode == "cancel")
                assertThrows(CancellationException::class.java) {
                    replace(f, original, command = command)
                }
            else
                assertInstanceOf(Result.Failed::class.java, replace(f, original, command = command))
            assertTrue(wrote, mode)
            assertEquals(empty, f.counts(), mode)
        }
        f.clock.at = at
        assertEquals(0L, f.storage.success(f.bootstrap(original.operationId)).version)
    }

    private fun intent(f: SpaceGatewayBootstrapFixture, gateway: Long? = null, build: Int = 5) =
        CollaborationSpaceGatewayBootstrapIntent(
            UUID.randomUUID(),
            f.storage.space,
            f.storage.account,
            f.storage.parent,
            f.storage.device,
            1,
            gateway,
            f.client.copy(build = build),
        )

    private fun replace(
        f: SpaceGatewayBootstrapFixture,
        original: CollaborationSpaceGatewayBootstrapIntent,
        operation: UUID = UUID.randomUUID(),
        observed: Long? = null,
        build: Int = 6,
        command: BootstrapNativeCollaborationSpaceGateway = f.command(),
    ) =
        command.execute(
            f.storage.actor,
            f.storage.parent,
            0,
            f.storage.space,
            operation,
            f.storage.device,
            1,
            observed,
            f.client.copy(build = build),
            original,
        )

    private fun assertChanged(result: Result<*>) {
        assertEquals("collaboration_space_gateway_changed", (result as Result.Failed).failure.code)
    }

    private fun rejected() = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_write_failed"))
}
